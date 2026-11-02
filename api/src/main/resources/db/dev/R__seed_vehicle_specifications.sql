INSERT INTO specifications (name, description) VALUES
    ('ADAPTIVE CRUISE CONTROL', 'Maintains a selected speed and following distance.'),
    ('ANDROID AUTO', 'Connects a compatible Android phone to the dashboard display.'),
    ('APPLE CARPLAY', 'Connects a compatible iPhone to the dashboard display.'),
    ('AUTOMATIC TRANSMISSION', 'Changes gears automatically while driving.'),
    ('BLUETOOTH AUDIO', 'Streams audio wirelessly from a paired device.'),
    ('HEATED FRONT SEATS', 'Provides adjustable heating for the front seats.'),
    ('ISOFIX ANCHORS', 'Provides attachment points for compatible child seats.'),
    ('PARKING SENSORS', 'Alerts the driver to nearby obstacles while parking.'),
    ('REARVIEW CAMERA', 'Displays the area behind the vehicle when reversing.'),
    ('USB-C CHARGING', 'Provides USB-C charging ports for passenger devices.')
ON CONFLICT (name) DO NOTHING;
