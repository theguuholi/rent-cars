-- Create each demo car and its equipment atomically. Only rows inserted by
-- this statement can receive demo assignments, even when a manual edit races it.
WITH fleet (name, brand, description, license_plate, daily_rate_cents, fine_amount_cents, category_name, features) AS (
    VALUES
        ('Argo',    'Fiat',       'Compact hatchback for city trips.',    'DEV1A01', 12000, 3000, 'HATCHBACK', ARRAY['BLUETOOTH AUDIO']),
        ('Onix',    'Chevrolet',  'Five-door urban hatchback.',          'DEV1A02', 13500, 3500, 'HATCHBACK', ARRAY['ANDROID AUTO', 'REARVIEW CAMERA']),
        ('Polo',    'Volkswagen', 'Compact hatchback for daily travel.', 'DEV1A03', 14500, 3500, 'HATCHBACK', ARRAY['APPLE CARPLAY', 'USB-C CHARGING']),
        ('Corolla', 'Toyota',     'Comfortable midsize sedan.',          'DEV1A04', 21000, 5000, 'SEDAN', ARRAY['ADAPTIVE CRUISE CONTROL', 'AUTOMATIC TRANSMISSION', 'ISOFIX ANCHORS']),
        ('City',    'Honda',      'Compact sedan with a roomy trunk.',   'DEV1A05', 17500, 4000, 'SEDAN', ARRAY['REARVIEW CAMERA', 'APPLE CARPLAY']),
        ('Cronos',  'Fiat',       'Family sedan for weekend trips.',     'DEV1A06', 15050, 4000, 'SEDAN', ARRAY['PARKING SENSORS']),
        ('Kicks',   'Nissan',     'Compact SUV for family travel.',      'DEV1A07', 20500, 5000, 'SUV', ARRAY['ANDROID AUTO', 'ISOFIX ANCHORS', 'REARVIEW CAMERA']),
        ('Compass', 'Jeep',       'Midsize SUV for longer trips.',       'DEV1A08', 26000, 6500, 'SUV', ARRAY['HEATED FRONT SEATS', 'PARKING SENSORS', 'AUTOMATIC TRANSMISSION']),
        ('Strada',  'Fiat',       'Compact pickup with an open bed.',    'DEV1A09', 19500, 5000, 'PICKUP', ARRAY['USB-C CHARGING']),
        ('Hilux',   'Toyota',     'Large pickup for hauling cargo.',     'DEV1A10', 35000, 8000, 'PICKUP', ARRAY['BLUETOOTH AUDIO', 'PARKING SENSORS', 'REARVIEW CAMERA'])
), inserted AS (
    INSERT INTO cars (name, brand, description, license_plate, daily_rate_cents, fine_amount_cents, category_id)
    SELECT f.name, f.brand, f.description, f.license_plate, f.daily_rate_cents, f.fine_amount_cents, c.id
    FROM fleet f JOIN categories c ON c.name = f.category_name
    ON CONFLICT (license_plate) DO NOTHING
    RETURNING id, license_plate
)
INSERT INTO cars_specifications (car_id, specification_id)
SELECT i.id, s.id
FROM inserted i
JOIN fleet f ON f.license_plate = i.license_plate
JOIN specifications s ON s.name = ANY(f.features);
