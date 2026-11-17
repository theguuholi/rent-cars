import fnmatch
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("api_mutation_matrix", Path(__file__).resolve().parents[1] / "ci" / "api_mutation_matrix.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class MutationMatrixTest(unittest.TestCase):
    def test_given_current_api_when_partitioned_then_every_production_file_belongs_to_one_shard(self):
        source = Path("apps/api/src/main/java/com/github/theguuholi/rentcars")
        shards = module.mutation_matrix(source)["include"]
        for path in source.rglob("*.java"):
            name = "com.github.theguuholi.rentcars." + str(path.relative_to(source).with_suffix("")).replace("/", ".")
            matches = [s for s in shards if any(fnmatch.fnmatchcase(name, pattern) for pattern in s["targets"].split(","))]
            self.assertEqual(len(matches), 1, name)

    def test_given_new_package_and_no_root_classes_when_discovered_then_includes_it_without_configuration(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory)
            (source / "newfeature").mkdir()
            (source / "newfeature" / "Example.java").touch()
            (source / "empty").mkdir()
            self.assertEqual(module.mutation_matrix(source), {"include": [{"name": "newfeature", "targets": "com.github.theguuholi.rentcars.newfeature.*"}]})

    def test_given_empty_source_when_discovered_then_fails_instead_of_skipping_mutations(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "No API packages"):
                module.mutation_matrix(Path(directory))
