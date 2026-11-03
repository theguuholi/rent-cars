-- Populate the demo fleet after the specification catalog is seeded.
-- Keep selections already made in the local application.
WITH equipment (license_plate, brand, model, features) AS (
    VALUES
        ('DEV1A01', 'Fiat',       'Argo',    ARRAY['BLUETOOTH AUDIO']),
        ('DEV1A02', 'Chevrolet',  'Onix',    ARRAY['ANDROID AUTO', 'REARVIEW CAMERA']),
        ('DEV1A03', 'Volkswagen', 'Polo',    ARRAY['APPLE CARPLAY', 'USB-C CHARGING']),
        ('DEV1A04', 'Toyota',     'Corolla', ARRAY['ADAPTIVE CRUISE CONTROL', 'AUTOMATIC TRANSMISSION', 'ISOFIX ANCHORS']),
        ('DEV1A05', 'Honda',      'City',    ARRAY['REARVIEW CAMERA', 'APPLE CARPLAY']),
        ('DEV1A06', 'Fiat',       'Cronos',  ARRAY['PARKING SENSORS']),
        ('DEV1A07', 'Nissan',     'Kicks',   ARRAY['ANDROID AUTO', 'ISOFIX ANCHORS', 'REARVIEW CAMERA']),
        ('DEV1A08', 'Jeep',       'Compass', ARRAY['HEATED FRONT SEATS', 'PARKING SENSORS', 'AUTOMATIC TRANSMISSION']),
        ('DEV1A09', 'Fiat',       'Strada',  ARRAY['USB-C CHARGING']),
        ('DEV1A10', 'Toyota',     'Hilux',   ARRAY['BLUETOOTH AUDIO', 'PARKING SENSORS', 'REARVIEW CAMERA'])
)
INSERT INTO cars_specifications (car_id, specification_id)
SELECT c.id, s.id
FROM equipment e
JOIN cars c ON c.license_plate = e.license_plate AND c.brand = e.brand AND c.name = e.model
JOIN specifications s ON s.name = ANY(e.features)
WHERE NOT EXISTS (SELECT 1 FROM cars_specifications cs WHERE cs.car_id = c.id)
ON CONFLICT (car_id, specification_id) DO NOTHING;
