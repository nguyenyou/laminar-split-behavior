# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

Minimal Scala.js + Laminar frontend app built with scala-cli. Single-file project (`main.scala`) that renders to `index.html`.

## Build

Compile Scala.js to JavaScript:
```
scala-cli --power package main.scala -f -o main.js
```

Serve locally (open `index.html` in browser after compiling).

## Stack

- Scala 3.7.3, Scala.js platform, ES modules
- Laminar 17.2.1 (UI library)
- scalajs-dom 2.8.1
- scalafmt 3.7.15 (dialect: Scala 3)
