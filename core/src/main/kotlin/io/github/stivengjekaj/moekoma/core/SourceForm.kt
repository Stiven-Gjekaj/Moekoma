package io.github.stivengjekaj.moekoma.core

/**
 * A settings form of a source, in sections. An action of the user can change the form, so the source gives the form
 * again after each action. As in Asabunko.
 */
data class SourceForm(
    val id: String,
    /** True when the user must save the form. The changes then go to the source only when the user saves. */
    val submit: Boolean = false,
    val sections: List<Section>,
) {
    data class Section(val id: String, val header: String? = null, val footer: String? = null, val rows: List<Row>)

    data class Row(
        val id: String,
        val type: String,
        val title: String,
        val subtitle: String? = null,
        /** The text of a label, a link, or an input. */
        val value: String? = null,
        /** "included" or "excluded" for a choice of a list. */
        val mark: String? = null,
        val selectable: Boolean? = null,
        /** The value of a toggle. */
        val on: Boolean? = null,
        val form: String? = null,
        /** The text of an input is hidden, such as a password. */
        val secure: Boolean? = null,
        /** The question to ask before a button does its action. */
        val confirm: String? = null,
        val destructive: Boolean? = null,
        val number: Double? = null,
        val minimum: Double? = null,
        val maximum: Double? = null,
        val step: Double? = null,
        val url: String? = null,
        val login: Login? = null,
    ) {
        enum class Kind {
            /** A text, with a value or a mark. With `selectable`, a tap does the action of the row. */
            Label,
            Input,
            Toggle,
            Button,
            /** A row that opens the form with the ID `form`. */
            Link,
            /** A number between `minimum` and `maximum`, in steps of `step`. */
            Stepper,
            /** A row that opens the web page `url`. */
            Web,
            /** A sign-in of the kind of `login`. */
            Login,
            /** A row that the app cannot do. */
            Unsupported,
        }

        val kind: Kind get() = when (type) {
            "labelRow" -> Kind.Label
            "inputRow" -> Kind.Input
            "toggleRow" -> Kind.Toggle
            "buttonRow" -> Kind.Button
            "navigationRow" -> if (form == null) Kind.Label else Kind.Link
            "stepperRow" -> Kind.Stepper
            "linkRow" -> Kind.Web
            "loginRow" -> if (login == null) Kind.Unsupported else Kind.Login
            else -> Kind.Unsupported
        }
    }

    /** What the app must do for a sign-in row. */
    data class Login(
        /** "basic" for a user name and a password, "web" for a page in a web view, and "oauth" for a web sign-in that ends at `callbackScheme`. */
        val method: String,
        val url: String? = null,
        val callbackScheme: String? = null,
        val useEmail: Boolean? = null,
        val signedIn: Boolean,
        val username: String? = null,
        /** The app removes the cookies of the site at the sign-out. */
        val clearsCookies: Boolean? = null,
        /** The keys of the local storage of the page that the source reads after a web sign-in. */
        val storageKeys: List<String>? = null,
    )
}

/** What the user does to a row of a form. */
sealed interface FormInput {
    data object Tap : FormInput
    data class Text(val text: String) : FormInput
    data class Toggle(val on: Boolean) : FormInput
    data class Number(val value: Double) : FormInput
    /** A sign-in with a user name and a password. */
    data class Login(val username: String, val password: String) : FormInput
    /** The end of a sign-in in a web view: the cookies of the site and the values of its local storage. */
    data class Web(val cookies: Map<String, String>, val storage: Map<String, String>) : FormInput
    /** The end of an OAuth sign-in: the URL of the callback. */
    data class Callback(val url: String) : FormInput
    data object Logout : FormInput
}

class SourceFormException(message: String) : Exception(message) {
    companion object {
        fun noForm() = SourceFormException("This source has no settings form.")
        fun refused() = SourceFormException("The source did not accept the sign-in.")
        fun unknownRow() = SourceFormException("The setting is not in the form.")
    }
}
