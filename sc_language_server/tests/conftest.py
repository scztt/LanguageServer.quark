import os
import sys

# Modifies Python's import system to include the project's root directory in sys.path,
# allowing tests to import package modules as if running from the root directory.
sys.path.insert(0, os.path.abspath(os.path.dirname(os.path.dirname(__file__))))
