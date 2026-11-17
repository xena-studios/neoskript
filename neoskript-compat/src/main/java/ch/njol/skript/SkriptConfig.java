/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package ch.njol.skript;

import ch.njol.skript.config.Option;

/**
 * Skript's config options, as addons read them: Skript's defaults. NeoSkript has its own config; these don't
 * follow it.
 */
public class SkriptConfig {

    public static final Option<String> language = new Option<>("language", "english");
    public static final Option<Boolean> checkForNewVersion = new Option<>("check for new version", false);
    public static final Option<String> releaseChannel = new Option<>("release channel", "none");
    public static final Option<Boolean> enableEffectCommands = new Option<>("enable effect commands", false);
    public static final Option<String> effectCommandToken = new Option<>("effect command token", "!");
    public static final Option<Boolean> allowOpsToUseEffectCommands = new Option<>("allow ops to use effect commands", false);
    public static final Option<Boolean> logEffectCommands = new Option<>("log effect commands", false);
    public static final Option<Boolean> compressBackups = new Option<>("compress backups", false);
    public static final Option<Boolean> usePlayerUUIDsInVariableNames = new Option<>("use player UUIDs in variable names", false);
    public static final Option<Boolean> enablePlayerVariableFix = new Option<>("player variable fix", true);
    public static final Option<Boolean> listenCancelledByDefault = new Option<>("listen to cancelled events by default", false);
    public static final Option<Integer> numberAccuracy = new Option<>("number accuracy", 2);
    public static final Option<Integer> maxTargetBlockDistance = new Option<>("maximum target block distance", 100);
    public static final Option<Boolean> caseSensitive = new Option<>("case sensitive", false);
    public static final Option<Boolean> allowFunctionsBeforeDefs = new Option<>("allow function calls before definations", false);
    public static final Option<Boolean> disableObjectCannotBeSavedWarnings = new Option<>("disable variable will not be saved warnings", false);
    public static final Option<Boolean> disableMissingAndOrWarnings = new Option<>("disable variable missing and/or warnings", false);
    public static final Option<Boolean> disableUnreachableCodeWarnings = new Option<>("disable unreachable code warnings", false);
    public static final Option<Boolean> enableScriptCaching = new Option<>("enable script caching", false);
    public static final Option<Boolean> keepConfigsLoaded = new Option<>("keep configs loaded", false);
    public static final Option<Boolean> addonSafetyChecks = new Option<>("addon safety checks", false);
    public static final Option<Boolean> apiSoftExceptions = new Option<>("soft api exceptions", false);
    public static final Option<Boolean> enableTimings = new Option<>("enable timings", false);
    public static final Option<Boolean> caseInsensitiveVariables = new Option<>("case-insensitive variables", true);
    public static final Option<Boolean> caseInsensitiveCommands = new Option<>("case-insensitive commands", false);
    public static final Option<String> parseLinks = new Option<>("parse links in chat messages", "disabled");
    public static final Option<Boolean> colorResetCodes = new Option<>("color codes reset formatting", true);
    public static final Option<String> scriptLoaderThreadSize = new Option<>("script loader thread size", "0");
    public static final Option<Boolean> useTypeProperties = new Option<>("use type properties", true);
    public static final Option<Boolean> allowUnsafePlatforms = new Option<>("allow unsafe platforms", false);
    public static final Option<Boolean> enableLegacyTabCompletion = new Option<>("enable legacy tab completion event compatibility", true);
    public static final Option<Boolean> keepLastUsageDates = new Option<>("keep command last usage dates", false);
    public static final Option<Boolean> loadDefaultAliases = new Option<>("load default aliases", true);
    public static final Option<Boolean> executeFunctionsWithMissingParams = new Option<>("execute functions with missing parameters", true);
    public static final Option<Integer> runtimeErrorLimitTotal = new Option<>("runtime errors.total errors per frame", 8);
    public static final Option<Integer> runtimeWarningLimitTotal = new Option<>("runtime errors.total warnings per frame", 8);
    public static final Option<Integer> runtimeErrorLimitLine = new Option<>("runtime errors.errors from one line per frame", 2);
    public static final Option<Integer> runtimeWarningLimitLine = new Option<>("runtime errors.warnings from one line per frame", 2);
    public static final Option<Integer> runtimeErrorLimitLineTimeout = new Option<>("runtime errors.error spam timeout limit", 4);
    public static final Option<Integer> runtimeWarningLimitLineTimeout = new Option<>("runtime errors.warning spam timeout limit", 4);
    public static final Option<Integer> runtimeErrorTimeoutDuration = new Option<>("runtime errors.error timeout length", 10);
    public static final Option<Integer> runtimeWarningTimeoutDuration = new Option<>("runtime errors.warning timeout length", 10);
    public static final Option<Integer> variableChangesUntilSave = new Option<>("variable changes until save", 1000);
    public static final Option<Boolean> simplifySyntaxesOnParse = new Option<>("simplify syntax on parse", true);

    public SkriptConfig() {}
}
