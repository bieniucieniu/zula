{
  pkgs,
  lib,
  config,
  ...
}:

let
  secrets = config.secretspec.secrets;
  root = config.devenv.root;
in
{
  packages = [
    pkgs.git
    pkgs.secretspec
    pkgs.postgresql
    pkgs.curl
    pkgs.jq
    pkgs.bun
  ]
  # Docker CLI from Nix on Linux only. macOS: use Docker Desktop on PATH
  # (Nix docker client fights Desktop / wrong socket).
  ++ lib.optionals pkgs.stdenv.isLinux [
    pkgs.docker-client
    pkgs.docker-compose
  ];

  dotenv.disableHint = true;

  services.postgres = {
    enable = true;
    listen_addresses = "127.0.0.1";
    port = 5432;
    settings.wal_level = "logical";
    initialDatabases = [
      {
        name = "zula";
        user = secrets.DATABASE_USERNAME or "zula";
        pass = secrets.DATABASE_PASSWORD or "zula";
        # PowerSync logical replication (first Postgres init only).
        # https://devenv.sh/services/postgres/#servicespostgresinitialdatabasesinitialsql
        # Runs as cluster superuser (unix socket during setup) before initialScript.
        initialSQL = ''
          CREATE ROLE powersync_role WITH REPLICATION BYPASSRLS LOGIN PASSWORD '${secrets.POWERSYNC_REPLICATION_PASSWORD or "powersync"}';
          GRANT CONNECT ON DATABASE zula TO powersync_role;
          GRANT USAGE ON SCHEMA public TO powersync_role;
          GRANT SELECT ON ALL TABLES IN SCHEMA public TO powersync_role;
          GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO powersync_role;
          ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO powersync_role;
          ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO powersync_role;
          CREATE PUBLICATION powersync FOR ALL TABLES;
        '';
      }
      {
        name = "zula_powersync";
        user = secrets.POWERSYNC_STORAGE_USERNAME or "powersync";
        pass = secrets.POWERSYNC_STORAGE_PASSWORD or "powersync";
        initialSQL = ''
          GRANT CONNECT ON DATABASE zula_powersync TO powersync_role;
        '';
      }
    ];
  };

  # --- Profiles ---
  # devenv --profile backend up     → Postgres + Ktor
  # devenv --profile powersync up   → backend + PowerSync (:8081)
  # devenv --profile all up         → deps + Ktor + web + native
  profiles = {
    backend = {
      module = {
        processes.server = {
          exec = "${root}/gradlew :server:run";
          process-compose = {
            working_dir = root;
            depends_on = {
              postgres.condition = "process_healthy";
            };
          };
        };
      };
    };
    powersync = {
      extends = [ "backend" ];
      module = {
        processes.powersync = {
          exec = ''
            set -euo pipefail
            if ! command -v docker >/dev/null 2>&1; then
              echo "docker not on PATH (Mac: Docker Desktop; Linux: docker daemon + client)"
              exit 1
            fi
            if ! docker compose version >/dev/null 2>&1; then
              echo "docker compose not available"
              exit 1
            fi
            if ! docker info >/dev/null 2>&1; then
              echo "docker daemon not reachable"
              exit 1
            fi
            export PS_DATA_SOURCE_URI="''${PS_DATA_SOURCE_URI:-postgresql://powersync_role:''${POWERSYNC_REPLICATION_PASSWORD:-powersync}@host.docker.internal:5432/zula}"
            export PS_STORAGE_URI="''${PS_STORAGE_URI:-postgresql://''${POWERSYNC_STORAGE_USERNAME:-powersync}:''${POWERSYNC_STORAGE_PASSWORD:-powersync}@host.docker.internal:5432/zula_powersync}"
            export PS_JWKS_URI="''${PS_JWKS_URI:-http://host.docker.internal:8080/.well-known/jwks.json}"
            docker compose -f "${root}/powersync/docker-compose.yaml" up --abort-on-container-exit
          '';
          process-compose = {
            working_dir = "${root}/powersync";
            depends_on = {
              postgres.condition = "process_healthy";
              server.condition = "process_started";
            };
            readiness_probe = {
              http_get = {
                host = "127.0.0.1";
                port = 8081;
                path = "/probes/readiness";
              };
              initial_delay_seconds = 10;
              period_seconds = 5;
              timeout_seconds = 5;
              failure_threshold = 12;
            };
            shutdown = {
              command = "docker compose -f ${root}/powersync/docker-compose.yaml down";
              timeout_seconds = 30;
            };
          };
        };
      };
    };
    web = {
      extends = [ "backend" ];
      module = {
        processes.web = {
          exec = "cd apps/web && bun run dev";
          process-compose = {
            working_dir = "${root}/apps/web";
            depends_on.server.condition = "process_started";
          };
        };
      };
    };
    native = {
      extends = [ "backend" ];
      module = {
        processes.native = {
          exec = "cd apps/native && bun run dev";
          process-compose = {
            working_dir = "${root}/apps/native";
            depends_on.server.condition = "process_started";
          };
        };
      };
    };
    all = {
      extends = [
        "backend"
        "web"
        "native"
      ];
    };
  };

  env = secrets;

  scripts.pg = {
    packages = [ pkgs.postgresql ];
    exec = ''
      export PGHOST="''${PGHOST:-127.0.0.1}"
      export PGPORT="''${PGPORT:-5432}"
      export PGUSER="''${DATABASE_USERNAME:-zula}"
      export PGDATABASE="''${PGDATABASE:-zula}"
      export PGPASSWORD="''${DATABASE_PASSWORD:-''${PGPASSWORD:-zula}}"
      exec ${pkgs.postgresql}/bin/psql "$@"
    '';
  };

  scripts.gen-api.exec = ''
    cd "${root}"
    bun run gen:api
    bun run format
  '';

  scripts.gen-powersync.exec = ''
    cd "${root}"
    bun run gen:powersync
  '';

  enterShell = ''
    echo "zula devenv"
    echo "  secrets: secretspec provider=${toString (config.secretspec.provider or "unset")} profile=${
      toString (config.secretspec.profile or "unset")
    }"
    echo "  deps:    devenv --profile backend up   # or: zula-deps"
    echo "  sync:    devenv --profile powersync up # postgres + ktor + PowerSync :8081"
    echo "  all:     devenv --profile all up       # or: zula-all"
    echo "  api:     gen-api                       # orval + biome format (server must be up)"
    echo "  psync:   gen-powersync                 # AppSchema from running PowerSync"
    echo "  db:      psql                          # interactive (needs devenv up)"
    echo "  jdbc:    $DATABASE_JDBC_URL"
    echo "  app:     $APP_URL"
    echo "  powersync: $POWERSYNC_URL"
    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
      echo "  docker:  ok ($(docker compose version 2>/dev/null | head -n1))"
    else
      echo "  docker:  MISSING — needed for PowerSync profile (Mac: Docker Desktop; Linux: docker + compose)"
    fi
  '';

  enterTest = ''
    echo "Checking secretspec secrets are wired"
    test -n "$DATABASE_JDBC_URL"
    test -n "$APP_URL"
    test -n "$PROVIDER_TOKEN_ENCRYPTION_KEY"
    test -n "$POWERSYNC_URL"
    echo "Checking docker compose accessibility (warn-only if absent on CI without Docker)"
    if command -v docker >/dev/null 2>&1; then
      docker compose version
    else
      echo "SKIP: docker not installed in this environment"
    fi
    test -f "${root}/powersync/docker-compose.yaml"
    test -f "${root}/powersync/sync-config.yaml"
    test -f "${root}/packages/powersync/package.json"
    echo "OK"
  '';
}
