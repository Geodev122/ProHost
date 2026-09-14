# ProHost Firebase Authentication SMS Template Configuration

To style SMS verification messages sent to users' phones so they identify **ProHost mobile app** and include the mandatory security statement, follow these simple steps in the Firebase Console:

---

## 📱 1. Firebase Console Configuration Steps

1. Open **[Firebase Console](https://console.firebase.google.com/)**.
2. Select project **`prohost-f766f`** (Project Number: `646730915838`).
3. In the left navigation menu, go to **Authentication** → **Templates**.
4. Click on **SMS verification code**.
5. Click the edit (pencil) icon to edit the template.

---

## 💬 2. Custom SMS Template Text

Replace the default template text with the following customized message:

```text
%NUM% is your ProHost mobile app verification code. Do not share this code with anyone.
```

### Explanation of Variables:
- **`%NUM%`**: Automatically replaced by Firebase Authentication with the generated 6-digit OTP (e.g. `482901`).
- **`ProHost mobile app`**: Identifies the application clearly instead of showing a generic project ID or raw domain name.
- **`Do not share this code with anyone.`**: Prominently includes the security warning to protect users from social engineering attacks.

---

## ⚙️ 3. Sender Name / App Name Settings

In Firebase Console → **Project Settings** (gear icon) → **General**:
- **Public-facing name**: Set to **`ProHost mobile app`**
- **Support email**: Set to your registered support email address (e.g. `support@pro-host.tech` or `geodev122@gmail.com`).

Save your changes. All subsequent SMS verification messages sent to real phone numbers will now display the customized ProHost branding and security warning!
