package im.flare.rule

data class IscRuleInfo(
    val qualifiedName: String,
    val imports: String,
    val helperMethods: String,
    val executeBody: String,
    val type: String?,
    val tenant: String?,
    val nm: String?
) {
    // "{tenant} {type} {nm}"
    val name: String?
        get() = if (tenant != null && type != null && nm != null) "$tenant $type $nm" else null

    // "Rule - {type} - {name}"  (without extension)
    val fileName: String?
        get() = if (type != null && name != null) "Rule - $type - $name" else null
}
