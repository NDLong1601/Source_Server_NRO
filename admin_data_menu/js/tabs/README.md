# Tab modules

Each file owns the state and actions of one admin tab. Shared catalogs, checklists and
spawn/config helpers live in `../components/`.

Every module must call `RegisterTab(...)` with its view, panel, navigation metadata,
title, `onOpen` and `onRefresh` callbacks.
