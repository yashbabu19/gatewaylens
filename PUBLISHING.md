# Publish GatewayLens to your GitHub account

The GitHub connection available in the current chat can read repositories but rejected repository writes with HTTP 403, so this project has been prepared locally instead of pretending it was pushed.

## One-time setup

1. Open [GitHub's new repository page](https://github.com/new).
2. Create a **public** repository named `gatewaylens` under `yashbabu19`.
3. Leave **Add a README**, **Add .gitignore**, and **Choose a license** unchecked—the initial commit already contains all three.
4. On your Mac, download the `gatewaylens.bundle` artifact supplied with this project, then run:

   ```bash
   git clone ~/Downloads/gatewaylens.bundle gatewaylens
   cd gatewaylens
   git remote add origin https://github.com/yashbabu19/gatewaylens.git
   git push -u origin main
   ```

   Adjust the path to `gatewaylens.bundle` if your browser saves it elsewhere.

5. Open the repository's **Actions** tab. The `Java CI` workflow will run `mvn clean verify` on GitHub-hosted runners.

## Contribution graph note

The included initial commit uses the GitHub no-reply address format for this account. GitHub only attributes a commit when its author email is associated with your account and the commit is in the repository's default branch. Check the exact no-reply address shown in [GitHub email settings](https://github.com/settings/emails) if the contribution square does not appear; you may need to amend the author email before pushing. GitHub notes that a qualifying contribution can take up to 24 hours to appear: [official contribution criteria](https://docs.github.com/en/account-and-profile/reference/profile-contributions-reference).
