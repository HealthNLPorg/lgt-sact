# setuptools replaces distutils, which is removed in python 3.12.
from setuptools import setup

from setuptools import find_packages

setup(name='langgraph-timelines', version='1.0.0', packages=find_packages(where="src"))
