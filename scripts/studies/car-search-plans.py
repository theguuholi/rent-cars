#!/usr/bin/env python3
"""Measure CarRepository search predicates in a disposable PostgreSQL container."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--rows', type=int, default=100_000)
    args = parser.parse_args()
    if not 10_000 <= args.rows <= 1_000_000:
        parser.error('--rows must be between 10000 and 1000000')
    root = Path(__file__).resolve().parents[2]
    output = Path(tempfile.mkdtemp(prefix='rent-car-plans-'))
    container = 'rent-car-plans-' + uuid.uuid4().hex[:12]

    def docker(*command, **kwargs):
        return subprocess.run(['docker', *command], check=True, capture_output=True,
                              text=True, **kwargs).stdout.strip()

    def sql(statement):
        return docker('exec', '-i', container, 'psql', '-XAt', '-v', 'ON_ERROR_STOP=1',
                      '-U', 'postgres', '-d', 'lab', input=statement)

    try:
        docker('run', '-d', '--rm', '--name', container,
               '-e', 'POSTGRES_PASSWORD=disposable-lab-only', '-e', 'POSTGRES_DB=lab',
               'postgres:17')
        for _ in range(60):
            try:
                sql('SELECT 1;')
                break
            except subprocess.CalledProcessError:
                time.sleep(0.5)
        else:
            raise RuntimeError('PostgreSQL did not become ready')
        migrations = root / 'apps/api/src/main/resources/db/migration'
        files = sorted(migrations.glob('V*__*.sql'),
                       key=lambda path: int(path.name.split('__')[0][1:]))
        for path in files:
            sql(path.read_text())
        sql(f"""
            INSERT INTO cars(name,description,brand,license_plate,available,
                             daily_rate_cents,fine_amount_cents,category_id)
            SELECT CASE WHEN n % 1000 = 0 THEN 'Needle ' ELSE 'Car ' END || lpad(n::text, 8, '0'),
                   repeat('x', 120), 'LabBrand', 'LAB' || n,
                   n % 3 <> 0, 10000, 3000, c.id
            FROM generate_series(1, {args.rows}) n
            CROSS JOIN categories c WHERE c.name = 'SEDAN';
            ANALYZE cars;
            ANALYZE categories;
        """)
        metadata = {'postgres': sql('SELECT version();'), 'cars': args.rows,
                    'migrations': [path.name for path in files],
                    'settings': {name: sql('SHOW ' + name + ';') for name in
                                 ['shared_buffers', 'work_mem', 'random_page_cost',
                                  'max_parallel_workers_per_gather']}}
        (output / 'environment.json').write_text(json.dumps(metadata, indent=2))
        rows = []
        scenarios = [('rare-page', '%needle%', 0, False),
                     ('rare-count', '%needle%', 0, True),
                     ('broad-page', '%car%', 0, False),
                     ('short-page', '%ca%', 0, False),
                     ('deep-page', '%car%', 10000, False)]
        for stage in ['baseline', 'btree', 'trigram']:
            if stage == 'btree':
                sql('CREATE INDEX lab_name_btree ON cars(lower(name)); ANALYZE cars;')
            if stage == 'trigram':
                sql('DROP INDEX lab_name_btree; CREATE EXTENSION pg_trgm; '
                    'CREATE INDEX lab_name_trgm ON cars USING gin(lower(name) gin_trgm_ops); '
                    'ANALYZE cars;')
            for label, pattern, offset, count in scenarios:
                projection = 'count(*)' if count else 'c.*'
                pagination = '' if count else f'ORDER BY c.name, c.id OFFSET {offset} LIMIT 20'
                query = f"""SELECT {projection} FROM cars c JOIN categories category ON c.category_id=category.id
                    WHERE (false OR c.available=true)
                      AND lower(c.name) LIKE '{pattern}' ESCAPE '!'
                      AND lower(c.brand) LIKE '%' ESCAPE '!'
                      AND lower(category.name) LIKE '%' ESCAPE '!'
                    {pagination}"""
                (output / f'{label}.sql').write_text(query + ';\n')
                for repeat in range(1, 4):
                    plan = json.loads(sql('EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) ' + query))[0]
                    (output / f'{stage}-{label}-{repeat}.json').write_text(json.dumps(plan, indent=2))
                    scans = []
                    def walk(node):
                        if node.get('Relation Name') == 'cars':
                            scans.append(node)
                        for child in node.get('Plans', []):
                            walk(child)
                    walk(plan['Plan'])
                    visited = sum((node['Actual Rows'] + node.get('Rows Removed by Filter', 0)
                                   + node.get('Rows Removed by Index Recheck', 0))
                                  * node['Actual Loops'] for node in scans)
                    rows.append({'stage': stage, 'scenario': label, 'repeat': repeat,
                                 'execution_ms': plan['Execution Time'],
                                 'planning_ms': plan['Planning Time'],
                                 'result_rows': plan['Plan']['Actual Rows'],
                                 'car_rows_visited_approx': visited,
                                 'shared_hit': plan['Plan'].get('Shared Hit Blocks', 0),
                                 'shared_read': plan['Plan'].get('Shared Read Blocks', 0),
                                 'car_scan': '|'.join(node['Node Type'] for node in scans)})
            print(stage + ' measured', flush=True)
        with (output / 'summary.csv').open('w') as stream:
            writer = csv.DictWriter(stream, fieldnames=rows[0].keys(), lineterminator="\n")
            writer.writeheader()
            writer.writerows(rows)
        print('Reports: ' + str(output))
    finally:
        subprocess.run(['docker', 'rm', '-f', container], capture_output=True, check=False)


if __name__ == '__main__':
    main()
