# Settings Skill

Use Android settings screens carefully. Prefer read-only observation unless the
user explicitly asks to change a setting. Prefer `open_settings_panel` over a
fragile `open_app` + `tap` sequence when navigating to a supported panel (wifi,
bluetooth, accessibility, app_info, notifications, system_settings).

Allowed initial tools:

- `open_app`
- `open_settings_panel`
- `observe_screen`
- `tap`
- `scroll`
- `press_back`
- `wait_for_ui`
