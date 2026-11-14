# Third-party native components

This inventory records the native components Flint currently fetches at immutable Git revisions during CMake configuration. Flint's own platform loader is maintained in `native/src/main/cpp/flint_platform.c` and is not a third-party component.

## GLFW Android fork

- Project: MojoLauncher GLFW (GLFW 3.5.0 based Android fork)
- Repository: https://github.com/MojoLauncher/glfw
- Revision: `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6`
- License: zlib/libpng
- Upstream license file: `LICENSE.md`

Copyright (c) 2002-2006 Marcus Geelnard
Copyright (c) 2006-2019 Camilla Löwy

This software is provided 'as-is', without any express or implied warranty.
Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter and redistribute it freely,
subject to the conditions in the upstream LICENSE.md. The full notice is
retained in the pinned source fetched by CMake.

Flint applies a small configure-time adaptation to the Android backend so it
uses Flint's own public EGL/Vulkan library loader. The underlying GLFW fork
remains third-party code and is not represented as authored by Flint.

## GL4ES

- Project: GL4ES
- Repository: https://github.com/ptitSeb/gl4es
- Revision: `8b1a3da5d2331d0f88b70be25963182d46eed857`
- License: MIT
- Upstream license file: `LICENSE`

The upstream license and attribution are retained in the pinned source fetched
by CMake. See the upstream repository for the full copyright notice and terms.
