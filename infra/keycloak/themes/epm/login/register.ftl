<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm'); section>

  <#if section = "header">
    EPM — Create account

  <#elseif section = "form">

    <!-- ── Logo ── -->
    <div class="epm-logo">
      <div class="epm-logo-mark">
        <div class="epm-logo-glow"></div>
        <svg width="36" height="36" viewBox="0 0 30 30" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="flow-grad-reg" x1="3" y1="5" x2="27" y2="26" gradientUnits="userSpaceOnUse">
              <stop stop-color="oklch(0.65 0.26 285)"/>
              <stop offset="1" stop-color="oklch(0.78 0.18 200)"/>
            </linearGradient>
          </defs>
          <path d="M4 6 C20 5, 20 15, 15 15 C10 15, 10 25, 26 24" stroke="url(#flow-grad-reg)" stroke-width="2.8" stroke-linecap="round" fill="none"/>
          <path d="M22 21 L26 24 L22 27" stroke="url(#flow-grad-reg)" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          <circle cx="4" cy="6" r="2.4" fill="oklch(0.65 0.26 285)"/>
        </svg>
      </div>
      <div>
        <div class="epm-brand-name">EPM</div>
        <div class="epm-brand-sub">Project Management</div>
      </div>
    </div>

    <div style="text-align:center;margin-bottom:1.75rem;">
      <p class="epm-page-title">Create your account</p>
      <p class="epm-page-subtitle">Start managing projects smarter</p>
    </div>

    <!-- ── Alerts ── -->
    <#if message?has_content && !messagesPerField.existsError('firstName','lastName','email','username','password','password-confirm')>
      <div class="alert alert-${message.type}" role="alert">
        <span>${kcSanitize(message.summary)?no_esc}</span>
      </div>
    </#if>

    <form id="kc-register-form" action="${url.registrationAction}" method="post">

      <!-- First + Last name in a row -->
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:0.75rem;">
        <div class="form-group">
          <label for="firstName">First name</label>
          <input
            id="firstName"
            name="firstName"
            type="text"
            autocomplete="given-name"
            value="${(register.formData.firstName!'')}"
            placeholder="Jane"
            class="form-control"
            aria-invalid="<#if messagesPerField.existsError('firstName')>true<#else>false</#if>"
          />
          <#if messagesPerField.existsError('firstName')>
            <span class="has-error help-block">${kcSanitize(messagesPerField.get('firstName'))?no_esc}</span>
          </#if>
        </div>

        <div class="form-group">
          <label for="lastName">Last name</label>
          <input
            id="lastName"
            name="lastName"
            type="text"
            autocomplete="family-name"
            value="${(register.formData.lastName!'')}"
            placeholder="Doe"
            class="form-control"
            aria-invalid="<#if messagesPerField.existsError('lastName')>true<#else>false</#if>"
          />
          <#if messagesPerField.existsError('lastName')>
            <span class="has-error help-block">${kcSanitize(messagesPerField.get('lastName'))?no_esc}</span>
          </#if>
        </div>
      </div>

      <!-- Email -->
      <#if !realm.registrationEmailAsUsername>
        <div class="form-group">
          <label for="username">Username</label>
          <input
            id="username"
            name="username"
            type="text"
            autocomplete="username"
            value="${(register.formData.username!'')}"
            placeholder="your-username"
            class="form-control"
            aria-invalid="<#if messagesPerField.existsError('username')>true<#else>false</#if>"
          />
          <#if messagesPerField.existsError('username')>
            <span class="has-error help-block">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
          </#if>
        </div>
      </#if>

      <div class="form-group">
        <label for="email">Email</label>
        <input
          id="email"
          name="email"
          type="email"
          autocomplete="email"
          value="${(register.formData.email!'')}"
          placeholder="jane@company.com"
          class="form-control"
          aria-invalid="<#if messagesPerField.existsError('email')>true<#else>false</#if>"
        />
        <#if messagesPerField.existsError('email')>
          <span class="has-error help-block">${kcSanitize(messagesPerField.get('email'))?no_esc}</span>
        </#if>
      </div>

      <!-- Password -->
      <#if passwordRequired??>
        <div class="form-group">
          <label for="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autocomplete="new-password"
            placeholder="Create a strong password"
            class="form-control"
            aria-invalid="<#if messagesPerField.existsError('password','password-confirm')>true<#else>false</#if>"
          />
          <#if messagesPerField.existsError('password')>
            <span class="has-error help-block">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
          </#if>
        </div>

        <div class="form-group">
          <label for="password-confirm">Confirm password</label>
          <input
            id="password-confirm"
            name="password-confirm"
            type="password"
            autocomplete="new-password"
            placeholder="Repeat your password"
            class="form-control"
            aria-invalid="<#if messagesPerField.existsError('password-confirm')>true<#else>false</#if>"
          />
          <#if messagesPerField.existsError('password-confirm')>
            <span class="has-error help-block">${kcSanitize(messagesPerField.get('password-confirm'))?no_esc}</span>
          </#if>
        </div>
      </#if>

      <!-- Submit -->
      <input
        type="submit"
        value="Create account"
        class="btn-primary"
        style="margin-top:0.5rem;"
      />

    </form>

  <#elseif section = "info">
    <div id="kc-registration">
      Already have an account? <a href="${url.loginUrl}">Sign in</a>
    </div>
  </#if>

</@layout.registrationLayout>
