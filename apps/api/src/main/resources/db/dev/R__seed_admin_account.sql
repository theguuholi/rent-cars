INSERT INTO accounts (email, password_hash, role)
VALUES ('test@test', '$2y$10$bJhLaLAbLAOTQEwDax819us7F0gTerEMPBT2fE93pkeQ59QzjJtku', 'ADMIN')
ON CONFLICT (email) DO NOTHING;
