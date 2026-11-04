-- Disk catalogue: record files found on other storage (an external disk holding a copy of a
-- repository), kept in the database so they can be listed and searched while the disk is unplugged.

CREATE TABLE storage_location (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(200) NOT NULL,
  -- Which repository's layout this storage holds (1 sandbox, 2 main, 3 dismissed).
  repo INT NOT NULL,
  -- The folder scanned: the repository root on that storage, e.g. /media/hd1/repo3.
  root_path VARCHAR(1000) NOT NULL,
  note VARCHAR(1000) NULL,
  -- The scan whose rows are current; rows of any other token are a scan in progress or leftovers.
  scan_token VARCHAR(40) NULL,
  last_scan_at DATETIME NULL,
  last_scan_files INT NULL,
  last_scan_bytes BIGINT NULL,
  last_scan_records INT NULL,
  last_scan_error VARCHAR(1000) NULL,
  date_created DATETIME NOT NULL,
  last_updated DATETIME NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE catalog_file (
  id BIGINT NOT NULL AUTO_INCREMENT,
  location_id BIGINT NOT NULL,
  scan_token VARCHAR(40) NOT NULL,
  module_key VARCHAR(50) NOT NULL,
  record_id BIGINT NOT NULL,
  -- The record's folder under the location's root, as found: "R/tlk/1/101".
  record_folder VARCHAR(500) NOT NULL,
  -- Path inside the record's folder, however deep: "subfolder/2.mp3".
  rel_path VARCHAR(1000) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  size_bytes BIGINT NOT NULL,
  modified DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_catalog_record (module_key, record_id),
  KEY idx_catalog_location (location_id, scan_token),
  KEY idx_catalog_name (file_name),
  CONSTRAINT fk_catalog_location FOREIGN KEY (location_id) REFERENCES storage_location (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
