# WL Orders — admin app (all orders in one list)

An Android app for **you, the store owner**. Log in with the username/password stored in the
`wapp` table of your database, and see **every order from every customer** in one scrolling
list: customer name + phone, city, payment method, the items ordered, total, status and time.
Tap an order for full details (address, discounts, shipping, line items).

```
php-api/        PHP files that sit in front of your MySQL database (upload to hosting, e.g. public_html/api/)
android-app/    Open directly in Android Studio, or let GitHub Actions build the APK
.github/workflows/build-apk.yml   Builds a debug APK on every push to main
```

## Signed release APK (fewer install warnings)

A *debug* APK (what the workflow used to build) is debuggable and signed with a throw-away key, so
Android and Play Protect warn about it. Build a **signed release** APK instead:

1. On your computer (needs Java): `./scripts/generate-keystore.sh` - creates `wosux-release.keystore`
   and `keystore.b64`, and prints the 4 secrets to add.
2. GitHub repo -> Settings -> Secrets and variables -> Actions -> add `KEYSTORE_BASE64`,
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Back up `wosux-release.keystore` privately and never commit it (`.gitignore` already blocks it).
   If you lose it you can't update the installed app - users would have to uninstall first.
4. Push to `main` -> Actions -> download **wosux-release-apk** -> `app-release.apk`.

Release builds are HTTPS-only; `http://10.0.2.2` testing still works in debug builds
(`app/src/debug/AndroidManifest.xml`). If a phone has the old debug-signed app, uninstall it
once - Android refuses to update an app that was signed with a different key.

**What can't be removed:** an APK installed outside Google Play will still show Android's one-time
"Install unknown apps" permission and may show a Play Protect "unknown developer" prompt. That is
by design for every sideloaded app. Only publishing through Google Play (even as a private /
internal-testing release) avoids it completely.

## Home screen

After login the app opens `HomeActivity` (dashboard). **Latest orders** and **Products** are live. Latest orders calls the
same `get_orders.php` as the full list, shows the newest 6 orders as horizontal cards (alternating
blue / black), and tapping a card opens `OrderDetailActivity`. The **Orders** box below the cards opens the full list.
Pull down to refresh. The header, the Store Sales and Networking boxes and bottom nav are static placeholders; tapping Store Sales or Networking just shows a "coming soon" toast until you point it at a real screen (see `HomeActivity`).
The round categories icon next to **Products** opens the Categories popup (a bottom sheet with a 3-column
grid: `HomeCategoryAdapter.DEFAULT_CATEGORIES`, layouts `sheet_categories.xml` / `item_category.xml`). Picking a
category only moves the highlight for now; the product list isn't filtered yet.
The top-left grid icon opens a small Refresh / Log out menu.

**Products section:** `HomeActivity.loadProducts()` calls **`php-api/get_products.php`** (published products,
newest first; `?limit=`, `?category=` and `?status=all` are supported) and shows the newest
`MAX_PRODUCTS` (6) as tiles: icon (picked from the product's category in `HomeProductAdapter.iconFor`),
name, and the price the customer pays (discounted price when there is one). The product photo
(`products.front_image`) is loaded from `ApiConfig.PRODUCT_IMAGE_BASE_URL`
(`https://wosux.com/wl/backend/uploads/products/`) and fills the whole tile once it has loaded (name and price
sit under the photo); if a photo is missing or the address is wrong, the colored tile with the category icon stays. The edit button on each tile is not wired to
anything yet.

## Orders list and Order details

The **Orders** box on Home opens `OrdersActivity`: a search box, status chips (All / Placed / Processing /
Preparing / Shipped / Delivered / Cancelled) and one row per order (avatar, name, `#number · N items`, total,
status pill). Pull down to refresh. Tapping a row - here or on a Home card - opens
`OrderDetailActivity`: hero card, status stepper, phone + address, items, totals and a bottom button.

The button moves the order one step forward
(`pending/paid -> processing -> preparing -> shipped -> delivered`) by calling
**`php-api/update_order_status.php`** (upload it with the other PHP files). Each tap writes the new
value into the `status` column of the `orders` table. Home and the list refresh themselves when you
go back. Status wording lives in `OrderStatus.java`.

**One-time database change:** `orders.status` is an ENUM that did not include `preparing`. Run
`sql/add_preparing_status.sql` once (phpMyAdmin -> your database -> SQL) **before** using the new
button, otherwise the update fails with a server error. Existing orders keep their status.

## How login works

1. The app sends the username + password to `login.php`.
2. `login.php` looks them up in the **`wapp`** table (`id`, `user`, `password`).
3. On success it returns a signed **token** (valid 30 days). The app stores it and sends it with
   every request (`X-Auth-Token` header). `get_orders.php` and `get_order_detail.php` refuse to
   answer without a valid token, so nobody can read your orders just by knowing the URL.
4. A token stops working if it expires, if you change `TOKEN_SECRET`, or if you change that
   admin's password in `wapp`. The app then sends you back to the login screen.

No database changes are needed — the `wapp` table is used exactly as it is.
To add another admin, just insert another row into `wapp`.

## Part 1 — Upload the PHP files

1. Run `sql/add_preparing_status.sql` once (see "Orders list and Order details" above).
2. Upload everything in `php-api/` to your server's `api/` folder (overwrite the old files) -
   new/changed this time: `get_products.php` and `update_order_status.php`.
3. Edit `config.php` on the server: your DB details, and **`TOKEN_SECRET`** — a long random
   string (32+ characters). Login will refuse to work until you change it from the placeholder.
4. **Delete `create_test_user.php` from the server.** It is no longer used, and anyone holding
   its key could reset customer passwords with it.
5. Make sure `.htaccess` uploaded too (it blocks browser access to `config.php`).

### Test it before touching the app

```bash
# 1) log in - copy the "token" value from the reply
curl -X POST https://yourdomain.com/api/login.php \
  -H "Content-Type: application/json" \
  -d '{"user":"wadmin","password":"YOUR_PASSWORD"}'

# 2) all orders, with items
curl https://yourdomain.com/api/get_orders.php -H "X-Auth-Token: PASTE_TOKEN_HERE"

# 3) without a token it must be refused (HTTP 401)
curl -i https://yourdomain.com/api/get_orders.php
```

## Part 2 — The Android app

`android-app/app/src/main/java/com/wl/orders/ApiConfig.java` → `BASE_URL` must point at the
folder holding the PHP files (currently `https://wosux.com/api/`).

- **Android Studio:** File → Open → select `android-app`, let Gradle sync, press ▶.
- **GitHub Actions:** push this repo to GitHub → Actions tab → *Build debug APK* → download the
  `wl-orders-debug-apk` artifact → install `app-debug.apk` on your phone.

In the app: pull down to refresh, ⋮ menu → Refresh / Log out.

## Security notes

- **Change the `wapp` password.** It is stored as plain text (and `1234admin` is easy to guess).
  `login.php` also accepts a bcrypt hash in that column, so you can upgrade later without any
  code change. Changing the password logs out all existing sessions.
- Never commit `config.php` with real credentials to GitHub — the copy in this repo has
  placeholders; keep the real one on the server only.
- The app talks to your server over HTTPS. Cleartext (`http://`) is now allowed in debug builds only.
- Use the signed release APK (see above) for installing on phones. For the Play Store you'd build
  a release AAB (`gradle bundleRelease`) with the same signing key.
