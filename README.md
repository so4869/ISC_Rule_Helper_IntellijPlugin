# ISC_Rule_Helper

IntelliJ IDEA plugin for SailPoint IdentityNow (ISC) rule development.  
Automates converting Java rule classes into SailPoint Rule XML, running the SailPoint Rule Validator, and fetching OAuth2 tokens.

---

## Requirements

- IntelliJ IDEA 2025.3 or later (Java plugin required)
- Java project with Java plugin enabled

---

## Features

### 1. ISC Create XML and Validate Cloud Execution Rule (Run Configuration)

Scans Java classes in specified base packages, generates Rule XML files, and validates them with the SailPoint Rule Validator executable.

**Class eligibility**

A Java class is picked up when it contains a `boolean` field named `VALIDATE` initialized to `true`. Access modifier and `static` keyword are ignored.

```java
public class MyCloudRule {
    public static boolean VALIDATE = true;

    public static String type = "Cloud";
    public static String tenant = "myTenant";
    public static String nm = "MyRule";

    // Helper methods and the execute() method are extracted automatically.
    public Object execute(SailPointContext context, Map params) throws GeneralException {
        // rule body
    }
}
```

**Computed names**

| Field | Value |
|-------|-------|
| `name` | `{tenant} {type} {nm}` |
| `fileName` | `Rule - {type} - {name}` |
| Output XML | `{fileName}.xml` |
| Validator output | `{fileName}.xml.validator.out` |

`type`, `tenant`, and `nm` fields can have any access modifier and may be static or instance fields.

**Configuration options**

| Field | Description |
|-------|-------------|
| Base Packages | Comma-separated packages to scan (e.g. `com.example.rules`) |
| Output Directory | Where XML and `.validator.out` files are written. Defaults to `{projectDir}/output` if blank. |
| Create new directory for each execution | Optionally create a sub-directory per run using a `DateTimeFormatter` pattern (e.g. `yyyy-MM-dd_HH-mm-ss`) or a static name. Existing files are overwritten; the directory itself is never deleted. |
| Executable | Path to the SailPoint Rule Validator executable |
| Java Home | `JAVA_HOME` passed to the validator process. Defaults to the project SDK if blank. |

**Validator invocation**

```
{executable} --file {xmlFilePath}
```

The working directory is set to the directory containing the executable so relative file references inside the validator work correctly.

---

### 2. ISC Connection (Run Configuration)

Authenticates against a SailPoint IdentityNow tenant using OAuth2 `client_credentials` and prints the access token to the Run console.

**Configuration options**

| Field | Description |
|-------|-------------|
| Tenant URL | Full tenant URL, e.g. `https://mytenant.identitynow.com` |
| Client ID | OAuth2 client ID |
| Client Secret | Stored in the OS keychain via IntelliJ PasswordSafe — never saved to workspace XML |

Token endpoint is derived automatically: `https://{tenantPrefix}.api.identitynow.com/oauth/token`

---

### 3. Validate Rule (Context Menu Action)

Right-click a Java file in the editor, Project view, or directory tree to validate the rule immediately using a specific run configuration's validator executable.

The submenu lists all **ISC Create XML and Validate Cloud Execution Rule** run configurations that have a validator executable configured. Selecting one generates the Rule XML on the fly, runs the validator, and shows the output in a dialog.

The action is visible only when the current file contains an eligible class (`VALIDATE = true`).

---

## Build

```bash
./gradlew build            # Compile and package
./gradlew buildPlugin      # Build distributable ZIP
./gradlew runIde           # Launch sandbox IDE with plugin loaded
./gradlew test             # Run tests
./gradlew verifyPlugin     # Check plugin compatibility
./gradlew publishPlugin    # Publish to JetBrains Marketplace
```

---

## Rule XML format

The generated XML follows the SailPoint Rule DTD:

```xml
<?xml version='1.0' encoding='UTF-8'?>
<!DOCTYPE Rule PUBLIC "sailpoint.dtd" "sailpoint.dtd">
<Rule language="beanshell" name="..." type="...">
  <Source><![CDATA[
    // extracted source
  ]]></Source>
</Rule>
```

Source extraction:
- Imports from the Java file are prepended
- Helper methods (all methods except `execute`) are included
- Generic type parameters are stripped (BeanShell compatibility)
- The `execute()` method body is used as the rule body

---

## Project structure

```
src/main/kotlin/im/flare/
├── action/
│   ├── ValidateRuleAction.kt        # Per-config validation action
│   ├── ValidateRuleGroup.kt         # Context menu action group
│   └── ValidateRuleOutputDialog.kt  # Validator output dialog
├── rule/
│   ├── AnsiStripper.kt              # Strips ANSI escape codes from validator output
│   ├── IscClassScanner.kt           # PSI-based class scanner
│   ├── IscRuleInfo.kt               # Extracted rule data
│   ├── IscSourceExtractor.kt        # Java to rule source extractor
│   ├── IscTemplateProcessor.kt      # Rule XML template engine
│   └── IscXmlUtils.kt               # JDOM XML utilities
└── runconfig/
    ├── IscCloudRuleRunConfiguration.kt
    ├── IscCloudRuleRunConfigurationType.kt
    ├── IscCloudRuleRunState.kt
    ├── IscCloudRuleSettingsEditor.kt
    ├── IscRunConfiguration.kt
    ├── IscRunConfigurationType.kt
    ├── IscRunProfileState.kt
    └── IscSettingsEditor.kt
```
