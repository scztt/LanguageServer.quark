# sc-language-server

This python script is a stdio wrapper for the SuperCollider Language Server. Since the LSP Quark currently communicates
over UDP, this program exists to support LSP clients that support stdio, such as neovim.

See this github [issue](https://github.com/scztt/LanguageServer.quark/issues/9) for background info.

## Development

Create a new venv and install dependencies

    python -m venv .venv
    source .venv/bin/activate
    python -m pip install -r requirements.dev.txt

## User Installation

Download the Quark from within SuperCollider:

    Quarks.install("https://github.com/scztt/LanguageServer.quark");
    thisProcess.recompile;

After downloading/installing the LanguageServer Quark, locate the directory.

e.g. on MacOs this might be:

    ~/Library/Application\ Support/SuperCollider/downloaded-quarks/LanguageServer

Navigate to the sc_language_server directory within that:

    cd sc_language_server


### Global installation

Install the python program to give your system the `sc-language-server` command. This allows you to simply specify
the `sc-language-server` command itself (plus any arguments) in your editor's LSP configuration rather than a full path to this directory.

Two options for this are:

#### Pip Install

Run:

    python -m pip install .

This might not work with an externally managed installation (e.g. managed by homebrew). If that is the case, please try installing with [pipx](#using-pipx).

#### Using pipx

Follow the instructions to install [pipx](https://github.com/pypa/pipx), and then run:

    `pipx install .`

### Post installation

Once installed, the command will be available globally, but you will need to set this up to be executed by your editor.

As an example see the setup for [Neovim](#neovim-lsp-configuration)

## Command-line Arguments

The script accepts the following command-line arguments:

- `--sclang-path`: Path to the SuperCollider language executable (sclang).
  - Default: default value currently only provided for MacOS.

- `--config-path`: Path to the configuration file.
  - Default: default value currently only provided for MacOS.
  - Depending on how many Quarks your regular sclang config contains, it may be beneficial to point sc-language-server
    to a minimal sclang config which only loads LanguageServer (and its dependencies).

- `--send-port`: Port number for sending data.
  - Optional
  - If not set (along with an unset --receive-port), a free port will be found.

- `--receive-port`: Port number for receiving data.
  - Optional
  - If not set (along with an unset --send-port), a free port will be found.

- `--ide-name`: Name of the IDE.
  - NOTE: currently this must be set to 'vscode' (the default)

- `-v, --verbose`: Enable verbose output.

- `-l, --log-file`: Specify a log file to write output.
  - Optional

## Neovim LSP Configuration

An example neovim lsp configuration:

```lua
local configs = require('lspconfig.configs')

configs.supercollider = {
    default_config = {
        cmd = {
            "sc-language-server",
            "--log-file",
            "/tmp/sc_lsp_output.log",
            "--verbose",
            "--", -- indicates the args that follow are to be passed to sclang
            "-u", "57300",  -- e.g. custom UDP listening port for sclang
            "-l", "/Users/me/sclang_conf_lsp.yaml",  -- e.g. full path to config file
        },
        filetypes = {'supercollider'},
        root_dir = function(fname)
            return "/"
        end,
        settings = {},
    },
}
```
