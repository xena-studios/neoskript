/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
/**
 * Region ownership on Folia: reads of entities and blocks another thread owns fail with a runtime error, and
 * effects on them run on their own thread. Nothing here is used on Paper.
 */
package co.xenastudios.neoskript.runtime.region;
