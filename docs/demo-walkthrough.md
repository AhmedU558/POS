# Demo accounts and walkthrough

A dev-only seeder creates two accounts and a small stocked store, so the whole
product → stock → sale loop can be driven without building a store by hand first.

It is off by default, has no default passwords, and **refuses to run under the `prod` or
`production` profile** whatever the configuration says.

---

## 1. Start it

Pick your own passwords (minimum 12 characters) and run:

```bash
APP_DEMO_ENABLED=true APP_DEMO_ADMIN_PASSWORD='<choose-one>' APP_DEMO_CASHIER_PASSWORD='<choose-another>' docker compose -f docker-compose.yml -f docker-compose.app.yml up -d --build
```

The seeder refuses to start if either password is missing or under 12 characters — it will not
invent one, because a seeder with a built-in password puts publicly known credentials into every
database it touches.

Confirm it ran:

```bash
docker logs pos-backend 2>&1 | grep "Demo data seeded"
```

Running it again is a no-op. It never updates or deletes anything, and it will not reset the
password of an account that already exists — so if you change a demo password later, restarting
keeps your change.

---

## 2. Sign in

| Account | Role | Sees |
|---|---|---|
| `demo.admin` | Super Administrator | Everything — products, setup, purchasing, reports |
| `demo.cashier` | Cashier | The till, sales, customers. No setup, no products, no reports |

Sign in at `/login`. Both accounts skip the forced password change, because you chose the
passwords yourself moments earlier.

Signing in as **both** is the point: the second account is how you check the permission-aware UI
actually hides what it should, rather than trusting that it does.

---

## 3. What is already there

- **Demo Store**, with **Front Counter** (terminal) and **Register 1** (register)
- **5 products**, each with a scannable barcode and 40 units in stock:

| Product | SKU | Barcode | Price |
|---|---|---|---|
| Cola 330ml | `DEMO-COLA` | `5000112637922` | 1.20 |
| Still Water 500ml | `DEMO-WATER` | `5000112637939` | 0.80 |
| Ground Coffee 250g | `DEMO-COFFEE` | `5000112637946` | 5.99 |
| Salted Crisps 40g | `DEMO-CRISPS` | `5000112637953` | 0.95 |
| Chocolate Bar 45g | `DEMO-CHOC` | `5000112637960` | 1.10 |

- One demo customer (`DEMO-CUST-1`, with a credit limit) and one demo supplier
  (`DEMO-SUPP-1`)

Stock is seeded because `InventoryService.deductForSale` rejects a sale against a product with no
balance — products alone would give you a till that cannot sell anything.

---

## 4. Try the loop

**As `demo.cashier` — take a sale:**

1. **Register** → *Open a register* → enter an opening float → **Open register**
2. **Point of Sale** — the scan field is already focused. Type a barcode from the table above and
   press Enter; the item drops into the cart and the field clears for the next one. Or click a
   tile in the grid.
3. Adjust quantities with the +/− steppers. Add a customer if you want to.
4. **Pay** — the server prices the sale first, so the amount you tender against is the real total.
   Enter cash received and the change is worked out; or split across methods.
5. The receipt appears, with change due. **Next sale** returns you to a focused scan field.
6. **Register** → **Close register** → count the drawer → you get a Z report with the variance.

Reload the page mid-shift: the till stays open. That is `GET /register-sessions/current`
(AMD-043) doing its job — before it existed, a reload locked you out of your own open drawer.

**As `demo.admin` — the rest:**

- **Products** → *Add product*. Give it a barcode, save, then scan that barcode at the till.
- **Inventory** → *Receive stock*, or *Adjust stock* (enter what you counted; it works out the
  difference).
- **Suppliers** → add one → **Purchase orders** → *New purchase order* → open it → *Submit* →
  *Receive goods*. Receiving is what puts the stock on the shelf.
- **Setup** — stores, terminals and registers. Add a second register and watch the Register screen
  offer you the choice.
- **Sales** — find the sale you just took by date or receipt number, and reprint the receipt.

---

## 5. Remove it

The demo accounts are ordinary users with real permissions. Before this database is used for
anything real:

```bash
docker compose -f docker-compose.yml -f docker-compose.app.yml down -v
```

That drops the volume and the data with it. If you want to keep the database and remove only the
demo accounts, deactivate them from **Setup → Users** once a user-management screen exists, or
delete the `demo.admin` and `demo.cashier` rows directly.

Either way, unset `APP_DEMO_ENABLED` so a later restart does not recreate them.
