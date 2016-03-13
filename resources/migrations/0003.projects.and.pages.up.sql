CREATE TABLE IF NOT EXISTS projects (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at timestamptz DEFAULT current_timestamp,
  modified_at timestamptz DEFAULT current_timestamp,

  version bigint DEFAULT 0,

  "user" uuid NOT NULL REFERENCES users(id),
  name text NOT NULL
) WITH (OIDS=FALSE);

CREATE TABLE IF NOT EXISTS pages (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at timestamptz DEFAULT current_timestamp,
  modified_at timestamptz DEFAULT current_timestamp,

  "user" uuid NOT NULL REFERENCES users(id),
  project uuid NOT NULL REFERENCES projects(id),
  name text NOT NULL,
  data text,

  version bigint DEFAULT 0,

  width bigint NOT NULL,
  height bigint NOT NULL,
  layout text NOT NULL
) WITH (OIDS=FALSE);

CREATE TABLE IF NOT EXISTS pages_history (
  id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
  page uuid REFERENCES pages(id),
  created_at timestamptz,

  data text,
  version bigint DEFAULT 0
) WITH (OIDS=FALSE);

CREATE OR REPLACE FUNCTION handle_occ()
  RETURNS TRIGGER AS $occ$
  BEGIN
    IF (TG_OP = 'UPDATE') THEN
      IF (NEW.version != OLD.version) THEN
        RAISE EXCEPTION 'Version missmatch: expected % given %',
              OLD.version, NEW.version
          USING ERRCODE='P0002';
      ELSE
        NEW.version := NEW.version + 1;
      END IF;
      RETURN NEW;
    END IF;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
  END;
$occ$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION handle_project_change()
  RETURNS TRIGGER AS $projectchange$
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      DELETE FROM pages WHERE project = OLD.id;
      RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
      RETURN NEW;
    END IF;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
  END;
$projectchange$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION handle_page_change()
  RETURNS TRIGGER AS $pagechange$
  BEGIN
    IF (TG_OP = 'DELETE') THEN
      DELETE FROM pages_history WHERE page = OLD.id;
      RETURN OLD;
    ELSIF (TG_OP = 'UPDATE') THEN
      INSERT INTO pages_history (page, created_at, data, version)
        VALUES (OLD.id, OLD.modified_at, OLD.data, OLD.version);
      RETURN NEW;
    END IF;
    RETURN NULL; -- result is ignored since this is an AFTER trigger
  END;
$pagechange$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_modified_at()
  RETURNS TRIGGER AS $updt$
  BEGIN
    NEW.modified_at := current_timestamp;
    RETURN NEW;
  END;
$updt$ LANGUAGE plpgsql;

-- Changes

CREATE TRIGGER project_changes_tgr BEFORE UPDATE OR DELETE ON projects
  FOR EACH ROW EXECUTE PROCEDURE handle_project_change();

CREATE TRIGGER page_changes_tgr BEFORE UPDATE OR DELETE ON pages
  FOR EACH ROW EXECUTE PROCEDURE handle_page_change();

-- OCC

CREATE TRIGGER project_occ_tgr BEFORE UPDATE OR DELETE ON projects
  FOR EACH ROW EXECUTE PROCEDURE handle_occ();

CREATE TRIGGER page_occ_tgr BEFORE UPDATE OR DELETE ON pages
  FOR EACH ROW EXECUTE PROCEDURE handle_occ();

-- Modified at

CREATE TRIGGER projects_modified_at_tgr BEFORE UPDATE ON projects
  FOR EACH ROW EXECUTE PROCEDURE update_modified_at();

CREATE TRIGGER pages_modified_at_tgr BEFORE UPDATE ON pages
  FOR EACH ROW EXECUTE PROCEDURE update_modified_at();
