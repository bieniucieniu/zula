{
  pkgs,
  lib,
  config,
  ...
}:

let
  secrets = config.secretspec.secrets;
  root = config.devenv.root;
  defaultDevAuthSecret = "local-dev-bypass";
  defaultDevAuthEmail = "dev@zula.local";
  devAuthSecret = secrets.AUTH_DEV_BYPASS_SECRET or defaultDevAuthSecret;
  devAuthEmail = secrets.AUTH_DEV_BYPASS_EMAIL or defaultDevAuthEmail;
  syncSchema = ''
    set -eu
    cd "${root}"
    bun run gen:api
    bun run format
    bun run build:packages
  '';
in
{
  # System Xcode for Expo/iOS. Nix apple-sdk + clang-wrapper leak DEVELOPER_DIR,
  # SDKROOT, and NIX_CFLAGS_COMPILE (libcxx) into xcodebuild → FP_NAN / uint8_t errors.
  apple.sdk = null;
  stdenv = if pkgs.stdenv.isDarwin then pkgs.stdenvNoCC else pkgs.stdenv;

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
  profiles =
    let
      expoSetup = ''
        set -eu
        if [ -z "''${EXPO_PUBLIC_API_URL:-}" ]; then
          lan_ip="$(${root}/scripts/lan-ip.sh || true)"
          host="''${lan_ip:-127.0.0.1}"
          export EXPO_PUBLIC_API_URL="http://''${host}:8000/api"
        fi
        export EXPO_PUBLIC_DEV_AUTH_SECRET="''${EXPO_PUBLIC_DEV_AUTH_SECRET:-''${AUTH_DEV_BYPASS_SECRET:-${defaultDevAuthSecret}}}"
        export EXPO_PUBLIC_DEV_AUTH_EMAIL="''${EXPO_PUBLIC_DEV_AUTH_EMAIL:-''${AUTH_DEV_BYPASS_EMAIL:-${defaultDevAuthEmail}}}"
        echo "native: EXPO_PUBLIC_API_URL=$EXPO_PUBLIC_API_URL"
        echo "native: dev auth bypass enabled ($EXPO_PUBLIC_DEV_AUTH_EMAIL)"
      '';
    in
    {
      backend = {
        module = {
          processes.server = {
            after = [ "devenv:processes:postgres" ];
            exec = "${root}/gradlew :server:run";
            ready = {
              http.get = {
                port = 8000;
                path = "/health";
              };
            };
          };
        };
      };
      schema = {
        extends = [ "backend" ];
        module = {
          processes.schema-sync = {
            after = [ "devenv:processes:server" ];
            exec = syncSchema;
          };
        };
      };
      web = {
        extends = [ "schema" ];
        module = {
          processes.web = {
            after = [
              "devenv:processes:schema-sync@completed"
              "devenv:processes:server"
            ];
            exec = "cd apps/web && bun run dev --host";
          };
        };
      };
      "native:ios" = {
        extends = [ "schema" ];
        module = {
          processes.native = {
            after = [
              "devenv:processes:schema-sync@completed"
              "devenv:processes:server"
            ];
            exec = expoSetup + ''
              cd ${root}/apps/native && bun run ios
            '';
          };
        };
      };
      "native:android" = {
        extends = [ "schema" ];
        module = {
          processes.native = {
            after = [
              "devenv:processes:schema-sync@completed"
              "devenv:processes:server"
            ];
            exec = expoSetup + ''
              cd ${root}/apps/native && bun run android
            '';
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

  env = secrets // {
    FORCE_COLOR = "1";
    CLICOLOR_FORCE = "1";
    AUTH_DEV_BYPASS_SECRET = devAuthSecret;
    AUTH_DEV_BYPASS_EMAIL = devAuthEmail;
    EXPO_PUBLIC_DEV_AUTH_SECRET = secrets.EXPO_PUBLIC_DEV_AUTH_SECRET or devAuthSecret;
    EXPO_PUBLIC_DEV_AUTH_EMAIL = secrets.EXPO_PUBLIC_DEV_AUTH_EMAIL or devAuthEmail;
  };

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
    ${lib.optionalString pkgs.stdenv.isDarwin ''
      # Belt-and-suspenders: never let Nix Darwin toolchain override Xcode for iOS builds.
      unset DEVELOPER_DIR SDKROOT NIX_APPLE_SDK_VERSION
      unset NIX_CFLAGS_COMPILE NIX_CFLAGS_COMPILE_FOR_TARGET
      unset NIX_CXXSTDLIB_COMPILE NIX_LDFLAGS NIX_LDFLAGS_FOR_TARGET
      unset CPATH C_INCLUDE_PATH CPLUS_INCLUDE_PATH OBJC_INCLUDE_PATH
    ''}
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
    echo "  devauth: $AUTH_DEV_BYPASS_EMAIL (bypass secret set for server + native)"
    if lan_ip="$("${root}/scripts/lan-ip.sh" 2>/dev/null)"; then
      echo "  lan:     $lan_ip  # native uses http://$lan_ip:8000/api unless EXPO_PUBLIC_API_URL set"
    fi
    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
      echo "  docker:  ok ($(docker compose version 2>/dev/null | head -n1))"
    else
      echo "  docker:  MISSING — needed for bun run deps:docker (Mac: Docker Desktop; Linux: docker + compose)"
    fi
    ${lib.optionalString pkgs.stdenv.isDarwin ''
      echo "  xcode:   $(xcode-select -p 2>/dev/null || echo MISSING)  # system Xcode (apple.sdk=null)"
    ''}
  '';

  enterTest = ''
    echo "Checking secretspec secrets are wired"
    test -n "$DATABASE_JDBC_URL"
    test -n "$APP_URL"
    test -n "$PROVIDER_TOKEN_ENCRYPTION_KEY"
    test -n "$AUTH_DEV_BYPASS_SECRET"
    test -n "$EXPO_PUBLIC_DEV_AUTH_SECRET"

    echo "Checking docker compose accessibility (warn-only if CI without Docker)"
    if command -v docker >/dev/null 2>&1; then
      docker compose version
    else
      echo "SKIP: docker not installed in this environment"
    fi
    test -f "${root}/docker-compose.yaml"
    echo "OK"
  '';
}
