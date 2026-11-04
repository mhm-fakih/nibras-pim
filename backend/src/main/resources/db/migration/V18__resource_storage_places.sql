-- Where a resource physically is: room -> cabinet -> shelf. Three parameter lists (edited on the
-- Parameters page like every other lookup); a cabinet names its room, a shelf its cabinet.
CREATE TABLE storage_room (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(255) NULL,
  style LONGTEXT NULL,
  date_created DATETIME NOT NULL,
  last_updated DATETIME NOT NULL,
  deleted_on DATETIME NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE storage_cabinet (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(255) NULL,
  style LONGTEXT NULL,
  room_id BIGINT NULL,
  date_created DATETIME NOT NULL,
  last_updated DATETIME NOT NULL,
  deleted_on DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_cabinet_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE storage_shelf (
  id BIGINT NOT NULL AUTO_INCREMENT,
  version BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(255) NOT NULL,
  code VARCHAR(255) NULL,
  style LONGTEXT NULL,
  cabinet_id BIGINT NULL,
  date_created DATETIME NOT NULL,
  last_updated DATETIME NOT NULL,
  deleted_on DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_shelf_cabinet (cabinet_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The resource's place, at whatever depth is known (a shelf implies its cabinet and room; the app
-- keeps the three consistent on save).
ALTER TABLE book ADD COLUMN storage_room_id BIGINT NULL,
                 ADD COLUMN storage_cabinet_id BIGINT NULL,
                 ADD COLUMN storage_shelf_id BIGINT NULL,
                 ADD KEY idx_book_storage_shelf (storage_shelf_id),
                 ADD KEY idx_book_storage_cabinet (storage_cabinet_id),
                 ADD KEY idx_book_storage_room (storage_room_id);
