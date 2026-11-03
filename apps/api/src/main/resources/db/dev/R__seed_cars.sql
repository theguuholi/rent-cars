WITH fleet (name, brand, description, license_plate, daily_rate_cents, fine_amount_cents, category_name) AS (
    VALUES
        ('Argo',    'Fiat',       'Compact hatchback for city trips.',    'DEV1A01', 12000, 3000, 'HATCHBACK'),
        ('Onix',    'Chevrolet',  'Five-door urban hatchback.',          'DEV1A02', 13500, 3500, 'HATCHBACK'),
        ('Polo',    'Volkswagen', 'Compact hatchback for daily travel.', 'DEV1A03', 14500, 3500, 'HATCHBACK'),
        ('Corolla', 'Toyota',     'Comfortable midsize sedan.',          'DEV1A04', 21000, 5000, 'SEDAN'),
        ('City',    'Honda',      'Compact sedan with a roomy trunk.',   'DEV1A05', 17500, 4000, 'SEDAN'),
        ('Cronos',  'Fiat',       'Family sedan for weekend trips.',     'DEV1A06', 15050, 4000, 'SEDAN'),
        ('Kicks',   'Nissan',     'Compact SUV for family travel.',      'DEV1A07', 20500, 5000, 'SUV'),
        ('Compass', 'Jeep',       'Midsize SUV for longer trips.',       'DEV1A08', 26000, 6500, 'SUV'),
        ('Strada',  'Fiat',       'Compact pickup with an open bed.',    'DEV1A09', 19500, 5000, 'PICKUP'),
        ('Hilux',   'Toyota',     'Large pickup for hauling cargo.',     'DEV1A10', 35000, 8000, 'PICKUP')
)
INSERT INTO cars (name, brand, description, license_plate, daily_rate_cents, fine_amount_cents, category_id)
SELECT name, brand, description, license_plate, daily_rate_cents, fine_amount_cents,
       (SELECT c.id FROM categories c WHERE c.name = fleet.category_name)
FROM fleet
ON CONFLICT (license_plate) DO NOTHING;
