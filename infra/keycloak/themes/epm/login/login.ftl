<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>

  <#if section = "header">
    EPM — Sign in

  <#elseif section = "form">

    <!-- ── Logo + branding ── -->
    <div class="epm-logo">
      <div class="epm-logo-mark">
        <div class="epm-logo-glow"></div>
        <svg width="36" height="36" viewBox="0 0 30 30" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="flow-grad-login" x1="3" y1="5" x2="27" y2="26" gradientUnits="userSpaceOnUse">
              <stop stop-color="oklch(0.65 0.26 285)"/>
              <stop offset="0.5" stop-color="oklch(0.72 0.22 272)"/>
              <stop offset="1" stop-color="oklch(0.78 0.18 200)"/>
            </linearGradient>
            <filter id="flow-glow-login" x="-40%" y="-40%" width="180%" height="180%">
              <feGaussianBlur in="SourceGraphic" stdDeviation="1.4" result="blur"/>
              <feMerge>
                <feMergeNode in="blur"/>
                <feMergeNode in="SourceGraphic"/>
              </feMerge>
            </filter>
          </defs>
          <path
            d="M4 6 C20 5, 20 15, 15 15 C10 15, 10 25, 26 24"
            stroke="url(#flow-grad-login)"
            stroke-width="2.8"
            stroke-linecap="round"
            fill="none"
            filter="url(#flow-glow-login)"
          />
          <path
            d="M22 21 L26 24 L22 27"
            stroke="url(#flow-grad-login)"
            stroke-width="2.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            fill="none"
          />
          <circle cx="4" cy="6" r="2.4" fill="oklch(0.65 0.26 285)"/>
        </svg>
      </div>
      <div>
        <div class="epm-brand-name">EPM</div>
        <div class="epm-brand-sub">Project Management</div>
      </div>
    </div>

    <div style="text-align:center; margin-bottom:1.75rem;">
      <p class="epm-page-title">Welcome back</p>
      <p class="epm-page-subtitle">Sign in to your workspace</p>
    </div>

    <!-- ── Alerts ── -->
    <#if messagesPerField.existsError('username','password')>
      <div class="alert alert-error" role="alert">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" style="flex-shrink:0;margin-top:1px">
          <circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="1.5"/>
          <path d="M8 4.5v4M8 10.5v1" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
        <span>${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}</span>
      </div>
    </#if>

    <#if message?has_content && (message.type != 'warning' || !isAppInitiatedAction??) && !messagesPerField.existsError('username','password')>
      <div class="alert alert-${message.type}" role="alert">
        <span>${kcSanitize(message.summary)?no_esc}</span>
      </div>
    </#if>

    <!-- ── Login form ── -->
    <form id="kc-form-login" action="${url.loginAction}" method="post">

      <!-- Username / Email -->
      <div class="form-group">
        <label for="username">
          <#if !realm.loginWithEmailAllowed>Username
          <#elseif !realm.registrationEmailAsUsername>Username or email
          <#else>Email</#if>
        </label>
        <input
          id="username"
          name="username"
          type="text"
          autocomplete="username"
          autofocus
          value="${(login.username!'')}"
          placeholder="<#if !realm.loginWithEmailAllowed>Enter your username<#elseif !realm.registrationEmailAsUsername>Enter username or email<#else>Enter your email</#if>"
          class="form-control<#if messagesPerField.existsError('username')> error</#if>"
          aria-invalid="<#if messagesPerField.existsError('username')>true<#else>false</#if>"
        />
        <#if messagesPerField.existsError('username')>
          <span class="has-error help-block">${kcSanitize(messagesPerField.get('username'))?no_esc}</span>
        </#if>
      </div>

      <!-- Password -->
      <div class="form-group">
        <label for="password">Password</label>
        <div class="input-group">
          <input
            id="password"
            name="password"
            type="password"
            autocomplete="current-password"
            placeholder="Enter your password"
          class="form-control<#if messagesPerField.existsError('password')> error</#if>"
          aria-invalid="<#if messagesPerField.existsError('password')>true<#else>false</#if>"
          />
          <#if passwordResetSupported ?? && passwordResetSupported>
            <!-- password toggle injected by Keycloak JS -->
          </#if>
        </div>
        <#if messagesPerField.existsError('password')>
          <span class="has-error help-block">${kcSanitize(messagesPerField.get('password'))?no_esc}</span>
        </#if>
      </div>

      <!-- Options row: remember me + forgot password -->
      <div id="kc-form-options" style="display:flex;align-items:center;justify-content:space-between;margin-top:0.25rem;">
        <#if realm.rememberMe && !usernameEditDisabled??>
          <div class="checkbox">
            <label>
              <input
                id="rememberMe"
                name="rememberMe"
                type="checkbox"
                <#if login.rememberMe??>checked</#if>
              />
              Remember me
            </label>
          </div>
        <#else>
          <span></span>
        </#if>

        <#if realm.resetPasswordAllowed>
          <a href="${url.loginResetCredentialsUrl}">Forgot password?</a>
        </#if>
      </div>

      <!-- Hidden fields -->
      <input type="hidden" name="credentialId" value="${(auth.selectedCredential!'')}"/>

      <!-- Submit -->
      <input
        id="kc-login"
        name="login"
        type="submit"
        value="Sign in"
        class="btn-primary"
      />

    </form>

  <#elseif section = "info">
    <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
      <div id="kc-registration">
        Don't have an account? <a href="${url.registrationUrl}">Create one</a>
      </div>
    </#if>
  </#if>

</@layout.registrationLayout>
