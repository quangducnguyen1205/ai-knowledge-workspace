/**
 * Points each field at the error message that belongs to it.
 *
 * The upstream templates already render the message next to the field, mark the control
 * `aria-invalid` and announce the text with `aria-live`. What they do not do is connect the two,
 * so a screen reader moving through the form by field reaches an invalid control and is told it
 * is invalid without being told why. Wiring `aria-describedby` here fixes that without forking a
 * template: the templates keep their own ids, and this only follows them.
 *
 * Two id shapes exist upstream. `input-error-<field>` names its field, and the sign-in form's
 * bare `input-error` belongs to every control the failure marked — on that form the email and the
 * password are both invalid and share one message, deliberately, so that a wrong password and an
 * unknown account stay indistinguishable.
 *
 * It adds nothing when there is no error, and leaves an existing `aria-describedby` in place.
 */
(function () {
  function describe(field, errorId) {
    if (!field || field.hasAttribute("aria-describedby")) return;
    field.setAttribute("aria-describedby", errorId);
  }

  function associateFieldErrors() {
    document.querySelectorAll('[id^="input-error"]').forEach(function (errorNode) {
      var fieldName = errorNode.id.replace(/^input-error-?/, "");

      if (fieldName) {
        describe(document.getElementById(fieldName), errorNode.id);
        return;
      }

      document.querySelectorAll("[aria-invalid='true']").forEach(function (field) {
        describe(field, errorNode.id);
      });
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", associateFieldErrors);
  } else {
    associateFieldErrors();
  }
})();
