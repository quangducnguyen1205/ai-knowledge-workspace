<#--
  Upstream ships this macro empty precisely so a theme can add a card footer, which makes it the
  one template override here that costs nothing to maintain.

  It repeats the reassurance the product's own sign-in surface gives, so the promise a visitor
  read a moment ago on the product does not disappear at the identity host. It is scoped to the
  pages that actually ask for a credential: on an error or an information page the same sentence
  would be a non-sequitur rather than a reassurance.
-->
<#macro content>
<#if pageId?? && ["login", "register", "login-reset-password", "login-update-password"]?seq_contains(pageId)>
<div class="p3-footer">
    <p class="p3-footer__line">${msg("p3PrivacyReassurance")}</p>
</div>
</#if>
</#macro>
