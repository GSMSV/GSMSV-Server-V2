ALTER TABLE users
    ADD COLUMN major VARCHAR(100),
    ADD COLUMN profile_image_file_name VARCHAR(80);

CREATE UNIQUE INDEX uq_users_profile_image_file_name
    ON users (profile_image_file_name)
    WHERE profile_image_file_name IS NOT NULL;
