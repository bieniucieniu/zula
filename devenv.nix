{
  pkgs,
  lib,
  config,
  ...
}:

let
  secrets = config.secretspec.secrets;
  root = config.devenv.root;
  syncSchema = ''
    set -eu
    cd "${root}"
    bun run gen:api
    bun run format
    bun run build:packages
  '';
in
{
  packages = [
    pkgs.git
    pkgs.secretspec
    pkgs.postgresql_18
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
    package = pkgs.postgresql_18;
    extensions = extensions: [ extensions.pgvector ];
    listen_addresses = "127.0.0.1";
    port = 5432;
    initialDatabases = [
      {
        name = "zula";
        user = secrets.DATABASE_USERNAME or "zula";
        pass = secrets.DATABASE_PASSWORD or "zula";
      }
    ];
  };

  # --- Profiles ---
  # devenv --profile backend up     → Postgres + Ktor
  # devenv --profile schema up      → backend + sync OpenAPI client + format + build packages
  # devenv --profile web up           → schema + Vite
  # devenv --profile native up        → schema + Expo
  # devenv --profile all up           → web + native
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
            readiness_probe = {
              http_get = {
                host = "127.0.0.1";
                port = 8000;
                path = "/health";
              };
              # Gradle cold start can take a while.
              initial_delay_seconds = 15;
              period_seconds = 5;
              timeout_seconds = 5;
              failure_threshold = 36;
            };
          };
        };
      };
    };
    schema = {
      extends = [ "backend" ];
      module = {
        processes.sync-schema = {
          exec = syncSchema;
          process-compose = {
            working_dir = root;
            depends_on = {
              server.condition = "process_healthy";
            };
            availability = {
              restart = "no";
            };
          };
        };
      };
    };
    web = {
      extends = [ "schema" ];
      module = {
        processes.web = {
          exec = "cd apps/web && bun run dev";
          process-compose = {
            working_dir = "${root}/apps/web";
            depends_on = {
              sync-schema.condition = "process_completed_successfully";
            };
          };
        };
      };
    };
    native = {
      extends = [ "schema" ];
      module = {
        processes.native = {
          # Expo inlines EXPO_PUBLIC_* at Metro start. Prefer explicit override;
          # else use LAN IP so a physical device can reach the host API.
          exec = ''
            set -eu
            if [ -z "''${EXPO_PUBLIC_API_URL:-}" ]; then
              lan_ip="$(${root}/scripts/lan-ip.sh || true)"
              host="''${lan_ip:-127.0.0.1}"
              export EXPO_PUBLIC_API_URL="http://''${host}:8000/api"
            fi
            echo "native: EXPO_PUBLIC_API_URL=$EXPO_PUBLIC_API_URL"
            cd ${root}/apps/native && bun run dev
          '';
          process-compose = {
            working_dir = "${root}/apps/native";
            depends_on = {
              sync-schema.condition = "process_completed_successfully";
            };
          };
        };
      };
    };
    all = {
      extends = [
        "web"
        "native"
      ];
    };
  };

  env = secrets;

  scripts.pg = {
    exec = ''
      export PGHOST="''${PGHOST:-127.0.0.1}"
      export PGPORT="''${PGPORT:-5432}"
      export PGUSER="''${DATABASE_USERNAME:-zula}"
      export PGDATABASE="''${PGDATABASE:-zula}"
      export PGPASSWORD="''${DATABASE_PASSWORD:-''${PGPASSWORD:-zula}}"
      exec psql "$@"
    '';
  };

  scripts.sync-schema.exec = syncSchema;

  scripts.gen-api.exec = ''
    cd "${root}"
    bun run gen:api
    bun run format
  '';

  enterShell = ''
    echo "zula devenv"
    echo "  secrets: secretspec provider=${toString (config.secretspec.provider or "unset")} profile=${
      toString (config.secretspec.profile or "unset")
    }"
    echo "  deps:    devenv --profile backend up   # postgres + ktor"
    echo "  schema:  devenv --profile schema up    # backend + sync OpenAPI client + build packages"
    echo "  web:     devenv --profile web up        # schema + vite"
    echo "  native:  devenv --profile native up     # schema + expo"
    echo "  docker:  bun run deps:docker            # postgres only (no devenv)"
    echo "  all:     devenv --profile all up        # web + native"
    echo "  sync:    sync-schema                   # orval + format + build packages (server must be up)"
    echo "  api:     gen-api                        # orval + biome format (server must be up)"
    echo "  db:      psql                          # interactive (needs devenv up)"
    echo "  jdbc:    $DATABASE_JDBC_URL"
    echo "  app:     $APP_URL"
    if lan_ip="$("${root}/scripts/lan-ip.sh" 2>/dev/null)"; then
      echo "  lan:     $lan_ip  # native uses http://$lan_ip:8000/api unless EXPO_PUBLIC_API_URL set"
    fi
    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
      echo "  docker:  ok ($(docker compose version 2>/dev/null | head -n1))"
    else
      echo "  docker:  MISSING — needed for bun run deps:docker (Mac: Docker Desktop; Linux: docker + compose)"
    fi
  '';

  enterTest = ''
    echo "Checking secretspec secrets are wired"
    test -n "$DATABASE_JDBC_URL"
    test -n "$APP_URL"
    test -n "$PROVIDER_TOKEN_ENCRYPTION_KEY"

    echo "Checking docker compose accessibility (warn-only if absent on CI without Docker)"
    if command -v docker >/dev/null 2>&1; then
      docker compose version
    else
      echo "SKIP: docker not installed in this environment"
    fi
    test -f "${root}/docker-compose.yaml"
    echo "OK"
  '';
}
