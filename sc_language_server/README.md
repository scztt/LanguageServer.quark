# sc-language-server

This python script is a stdio wrapper for the SuperCollider Language Server. Since the LSP Quark currently communicates
over UDP, this program exists to support lsp clients that support stdio, such as neovim.

See this github [issue](https://github.com/scztt/LanguageServer.quark/issues/9) for background info.

## Development

Create a new venv and install dependencies

    python -m venv .venv
    source .venv/bin/activate
    python -m pip install -r requirements.dev.txt

## User Installation

Pip install to give your system the `sc-language-server` command.

    python -m pip install .

Once installed, the  command will be available, but you will need to set this up to be executed by your editor.

## Neovim LSP Configuration

```lua
local configs = require('lspconfig.configs')

configs.supercollider = {
    default_config = {
        cmd = {"sc-language-server"}
        filetypes = {'supercollider'},
        root_dir = function(fname)
            return "/"
        end,
        settings = {},
    },
}
```
