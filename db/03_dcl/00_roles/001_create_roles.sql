-- NOLOGIN roles carry permissions. The worker_app login and password belong to infra.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'worker_reader') THEN
        CREATE ROLE worker_reader NOLOGIN;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'worker_writer') THEN
        CREATE ROLE worker_writer NOLOGIN;
    END IF;
END
$$;
