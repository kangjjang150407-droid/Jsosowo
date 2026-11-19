package com.jsosowo.winemu;

/**
 * Builds an autounattend.xml that makes Windows 11 Setup create a local account automatically:
 * no Microsoft account, no network requirement, no privacy questions, auto sign-in.
 * Disk selection and edition choice are left to the user so nothing is wiped without asking.
 */
final class Unattend {
    private Unattend() {}

    static String xml(boolean arm64, String user, String password) {
        String arch = arm64 ? "arm64" : "amd64";
        String u = esc(user == null || user.trim().isEmpty() ? "User" : user.trim());
        String p = esc(password == null ? "" : password);
        String comp = "processorArchitecture=\"" + arch + "\" publicKeyToken=\"31bf3856ad364e35\" "
                + "language=\"neutral\" versionScope=\"nonSxS\" "
                + "xmlns:wcm=\"http://schemas.microsoft.com/WMIConfig/2002/State\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<unattend xmlns=\"urn:schemas-microsoft-com:unattend\">\n"

                // --- windowsPE: accept EULA, skip hardware checks (useful when TPM is off)
                + "  <settings pass=\"windowsPE\">\n"
                + "    <component name=\"Microsoft-Windows-Setup\" " + comp + ">\n"
                + "      <UserData><AcceptEula>true</AcceptEula></UserData>\n"
                + "      <RunSynchronous>\n"
                + labConfig(1, "BypassTPMCheck")
                + labConfig(2, "BypassSecureBootCheck")
                + labConfig(3, "BypassRAMCheck")
                + labConfig(4, "BypassCPUCheck")
                + labConfig(5, "BypassStorageCheck")
                + "      </RunSynchronous>\n"
                + "    </component>\n"
                + "  </settings>\n"

                // --- specialize: allow finishing OOBE without network
                + "  <settings pass=\"specialize\">\n"
                + "    <component name=\"Microsoft-Windows-Deployment\" " + comp + ">\n"
                + "      <RunSynchronous>\n"
                + "        <RunSynchronousCommand wcm:action=\"add\"><Order>1</Order>"
                + "<Path>reg.exe add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\OOBE\" /v BypassNRO /t REG_DWORD /d 1 /f</Path>"
                + "</RunSynchronousCommand>\n"
                + "      </RunSynchronous>\n"
                + "    </component>\n"
                + "  </settings>\n"

                // --- oobeSystem: Korean region/keyboard, local admin account, auto logon
                + "  <settings pass=\"oobeSystem\">\n"
                + "    <component name=\"Microsoft-Windows-International-Core\" " + comp + ">\n"
                + "      <InputLocale>0412:00000412</InputLocale>\n"
                + "      <SystemLocale>ko-KR</SystemLocale>\n"
                + "      <UserLocale>ko-KR</UserLocale>\n"
                + "    </component>\n"
                + "    <component name=\"Microsoft-Windows-Shell-Setup\" " + comp + ">\n"
                + "      <TimeZone>Korea Standard Time</TimeZone>\n"
                + "      <OOBE>\n"
                + "        <HideEULAPage>true</HideEULAPage>\n"
                + "        <HideOEMRegistrationScreen>true</HideOEMRegistrationScreen>\n"
                + "        <HideOnlineAccountScreens>true</HideOnlineAccountScreens>\n"
                + "        <HideWirelessSetupInOOBE>true</HideWirelessSetupInOOBE>\n"
                + "        <ProtectYourPC>3</ProtectYourPC>\n"
                + "      </OOBE>\n"
                + "      <UserAccounts>\n"
                + "        <LocalAccounts>\n"
                + "          <LocalAccount wcm:action=\"add\">\n"
                + "            <Name>" + u + "</Name>\n"
                + "            <DisplayName>" + u + "</DisplayName>\n"
                + "            <Group>Administrators</Group>\n"
                + "            <Password><Value>" + p + "</Value><PlainText>true</PlainText></Password>\n"
                + "          </LocalAccount>\n"
                + "        </LocalAccounts>\n"
                + "      </UserAccounts>\n"
                + "      <AutoLogon>\n"
                + "        <Enabled>true</Enabled>\n"
                + "        <Username>" + u + "</Username>\n"
                + "        <Password><Value>" + p + "</Value><PlainText>true</PlainText></Password>\n"
                + "        <LogonCount>9999999</LogonCount>\n"
                + "      </AutoLogon>\n"
                + "    </component>\n"
                + "  </settings>\n"
                + "</unattend>\n";
    }

    private static String labConfig(int order, String value) {
        return "        <RunSynchronousCommand wcm:action=\"add\"><Order>" + order + "</Order>"
                + "<Path>reg.exe add HKLM\\SYSTEM\\Setup\\LabConfig /v " + value + " /t REG_DWORD /d 1 /f</Path>"
                + "</RunSynchronousCommand>\n";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
