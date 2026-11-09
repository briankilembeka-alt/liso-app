# Test server and two-phone call test

This sets up one small server in AWS Ireland (eu-west-1) that runs the Liso API, its database and HTTPS, then builds an app that talks to it. Nothing here needs access keys: the server gets its video permission from an AWS role.

**Before you start:** AWS must have finished verifying the account. Until then, EC2 and CloudShell refuse to start.

## 1. Let the image be downloaded (once, on GitHub)

After this branch is merged, the **API image** workflow publishes `liso-api` under the repository owner's **Packages** on GitHub. Open it, go to **Package settings**, and set **Change visibility** to **Public**. The image holds code only, with no keys or passwords, and this lets the server download it without a GitHub login.

## 2. Give the server video permission (AWS IAM)

1. In the AWS console, open **IAM**, then **Policies**, then **Create policy**. Choose **JSON** and paste:
   ```json
   {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["ivs:CreateStage","ivs:DeleteStage","ivs:GetStage","ivs:ListStages","ivs:CreateParticipantToken","ivs:TagResource"],"Resource":"*"}]}
   ```
   Name it `liso-video`.
2. Open **Roles**, then **Create role**. Choose **AWS service** and **EC2**, attach `liso-video`, and name the role `liso-server`.

## 3. Start the server (AWS EC2, Ireland)

1. Switch the region to **Europe (Ireland) eu-west-1**, then open **EC2** and tap **Launch instance**.
2. **Name:** `liso-test`. **Image:** Ubuntu Server 24.04 LTS. **Instance type:** `t3.micro`.
3. **Key pair:** "Proceed without a key pair". You can still open a terminal later with **EC2 Instance Connect**.
4. **Network settings:** tick **Allow HTTPS traffic from the internet** and **Allow HTTP traffic from the internet**. HTTP is needed for the certificate.
5. **Storage:** 20 GiB.
6. **Advanced details:** set **IAM instance profile** to `liso-server`. In **User data**, paste the whole of [`deploy/user-data.sh`](../deploy/user-data.sh).
7. Tap **Launch instance**. After about 5 minutes, take the instance's **Public IPv4 address**, for example `52.18.1.2`, and open `https://52-18-1-2.sslip.io/health` (dots become dashes). It should show `{"ok":true}`.

If it doesn't, open the instance, then **Connect**, then **EC2 Instance Connect**, and run `sudo tail -50 /var/log/liso-setup.log`.

**Cost:** stop the instance when you're not testing (**Instance state**, then **Stop**). It keeps its data, but it gets a new address when started again, so you'd need a new test APK.

## 4. Build the test app

1. On GitHub, open **Actions**, then **Test APK**, then **Run workflow**. Paste the server address, for example `https://52-18-1-2.sslip.io`, and run it.
2. When it finishes, download **liso-test-apks** from the run page. Inside there are two APKs: `arm64-v8a` for most phones from 2019 on, and `armeabi-v7a` for older ones.
3. Send the right APK to each phone (WhatsApp works) and install it. You'll need to allow installing from unknown sources.

## 5. Test a call on two phones (both Android 9 or newer)

The test server accepts the sign-in code **246810** for every number, so no SMS is needed.

**Phone A, the seller**
1. Open **Me**, sign in, and open a shop, choosing for example Phones.
2. Tap **Take video calls** and keep that screen open.

**Phone B, the buyer**
1. Tap **Shop now**, choose the same category, open the shop and tap **Video call**.
2. Sign in, enter any 14-character NIN (test only), and allow the camera and microphone.

**Phone A** sees the buyer waiting and taps **Accept**. Both phones should show each other's video and the red **Recorded** badge. After the call, phone A writes up the deal, phone B accepts it in **Deals** and gets a 4-digit code, phone A enters that code, and phone B taps **I got the item**.

Note anything that looks wrong, such as video quality on 3G or 4G, delay, sound or confusing wording, and send it in the project thread.

## Turning it off

To stop paying for the test server, choose **Instance state** then **Terminate** in EC2. That deletes the server and its test data.
