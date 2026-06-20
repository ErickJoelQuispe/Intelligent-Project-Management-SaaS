<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>

  <#if section = "header">
    EPM — Error

  <#elseif section = "form">

    <!-- ── Logo ── -->
    <div class="epm-logo">
      <div class="epm-logo-mark">
        <div class="epm-logo-glow"></div>
        <svg width="36" height="36" viewBox="0 0 30 30" fill="none" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <linearGradient id="flow-grad-err" x1="3" y1="5" x2="27" y2="26" gradientUnits="userSpaceOnUse">
              <stop stop-color="oklch(0.65 0.26 285)"/>
              <stop offset="1" stop-color="oklch(0.78 0.18 200)"/>
            </linearGradient>
          </defs>
          <path d="M4 6 C20 5, 20 15, 15 15 C10 15, 10 25, 26 24" stroke="url(#flow-grad-err)" stroke-width="2.8" stroke-linecap="round" fill="none"/>
          <path d="M22 21 L26 24 L22 27" stroke="url(#flow-grad-err)" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" fill="none"/>
          <circle cx="4" cy="6" r="2.4" fill="oklch(0.65 0.26 285)"/>
        </svg>
      </div>
      <div>
        <div class="epm-brand-name">EPM</div>
        <div class="epm-brand-sub">Project Management</div>
      </div>
    </div>

    <!-- ── Error card ── -->
    <div style="text-align:center;margin-bottom:1.5rem;">
      <div style="
        width:3rem;height:3rem;
        border-radius:50%;
        background:oklch(0.65 0.24 22 / 0.12);
        border:1px solid oklch(0.65 0.24 22 / 0.30);
        display:flex;align-items:center;justify-content:center;
        margin:0 auto 1rem;
      ">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="10" cy="10" r="9" stroke="oklch(0.80 0.18 22)" stroke-width="1.5"/>
          <path d="M10 5.5v5M10 12.5v1.5" stroke="oklch(0.80 0.18 22)" stroke-width="1.5" stroke-linecap="round"/>
        </svg>
      </div>
      <p class="epm-page-title">Something went wrong</p>
      <p class="epm-page-subtitle" style="margin-top:0.5rem;">
        ${message.summary?no_esc}
      </p>
    </div>

    <#if client?? && client.baseUrl?has_content>
      <a
        href="${client.baseUrl}"
        style="
          display:block;
          width:100%;
          padding:0.6875rem 1.5rem;
          background:oklch(0.16 0.022 268);
          border:1px solid oklch(0.23 0.018 268);
          border-radius:0.625rem;
          color:oklch(0.95 0.008 268);
          font-family:inherit;
          font-size:0.9375rem;
          font-weight:600;
          text-align:center;
          text-decoration:none;
          transition:background 0.15s ease, border-color 0.15s ease;
          margin-top:1rem;
        "
        onmouseover="this.style.background='oklch(0.21 0.020 268)';this.style.borderColor='oklch(0.33 0.020 268)'"
        onmouseout="this.style.background='oklch(0.16 0.022 268)';this.style.borderColor='oklch(0.23 0.018 268)'"
      >
        Back to application
      </a>
    </#if>

  </#if>

</@layout.registrationLayout>
