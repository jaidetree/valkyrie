{
  description = "Valkyrie";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, flake-utils, nixpkgs }: flake-utils.lib.eachDefaultSystem (system:
    let
      pkgs = import nixpkgs { inherit system; };
    in
    {
      devShell = pkgs.mkShell {
        buildInputs = [
          pkgs.aider-chat
          pkgs.clj-kondo
          pkgs.clojure
          pkgs.clojure-lsp
          pkgs.neil
          pkgs.nodejs_23
          pkgs.temurin-jre-bin-17
        ];
        shellHook = ''
          export PATH="node_modules/.bin":"$PATH";
        '';
      };

    });
}
