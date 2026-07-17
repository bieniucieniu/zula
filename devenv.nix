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
  ];

  # Secrets come from SecretSpec (provider=dotenv → `.env`), not devenv's dotenv module.
  dotenv.disableHint = true;

  languages.java.enable = true;

  # --- Service config (disabled in base; enabled by profiles) ---
  services.postgres = {
    enable = true;
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

  services.rabbitmq = {
    enable = true;
    listenAddress = "127.0.0.1";
    port = 5672;
    managementPlugin.enable = true;
  };

  # --- Profiles ---
  # devenv --profile backend up  → Postgres + RabbitMQ
  # devenv --profile all up      → deps + Ktor + web + native
  profiles = {
    all = {
      module = {
        processes.server = {
          exec = "${root}/gradlew :server:run";
          process-compose = {
            working_dir = root;
            depends_on = {
              postgres.condition = "process_healthy";
              rabbitmq.condition = "process_started";
            };
          };
        };

        processes.web = {
          exec = "bun run dev";
          process-compose = {
            working_dir = "${root}/apps/web";
            depends_on.server.condition = "process_started";
          };
        };

        processes.native = {
          exec = "bun run dev";
          process-compose = {
            working_dir = "${root}/apps/native";
            depends_on.server.condition = "process_started";
          };
        };
      };
    };
  };

  # --- Env from SecretSpec (.env via provider=dotenv in devenv.yaml) ---
  env = secrets;

  scripts.zula-deps.exec = ''
    echo "Starting backend deps (Postgres + RabbitMQ)…"
    exec devenv --profile backend up
  '';

  scripts.zula-all.exec = ''
    echo "Starting all: deps + server + web + native…"
    exec devenv --profile all up
  '';

  scripts.pg.exec = ''
    export PGHOST="''${PGHOST:-127.0.0.1}"
    export PGPORT="''${PGPORT:-5432}"
    export PGUSER="''${DATABASE_USERNAME:-zula}"
    export PGDATABASE="''${PGDATABASE:-zula}"
    export PGPASSWORD="''${DATABASE_PASSWORD:-''${PGPASSWORD:-zula}}"
    exec ${pkgs.postgresql}/bin/psql "$@"
  '';

  enterShell = ''
    echo "zula devenv"
    echo "  secrets: secretspec provider=${toString (config.secretspec.provider or "unset")} profile=${
      toString (config.secretspec.profile or "unset")
    }"
    echo "  deps:    devenv --profile backend up   # or: zula-deps"
    echo "  all:     devenv --profile all up       # or: zula-all"
    echo "  db:      psql                          # interactive (needs devenv up)"
    echo "  jdbc:    $DATABASE_JDBC_URL"
    echo "  app:     $APP_URL"
  '';

  enterTest = ''
    echo "Checking secretspec secrets are wired"
    test -n "$DATABASE_JDBC_URL"
    test -n "$APP_URL"
    test -n "$PROVIDER_TOKEN_ENCRYPTION_KEY"
    echo "OK"
  '';
}
