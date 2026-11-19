package com.jsosowo.winemu;

/**
 * Builds an autounattend.xml for Windows 11 Setup.
 * <ul>
 *   <li>install part (optional): language page, disk partitioning of disk 0 (the VM disk),
 *       edition choice via a generic install key — only used for a fresh, empty VM disk.</li>
 *   <li>OOBE part (optional): local admin account, no Microsoft account / network /
 *       privacy screens, automatic sign-in.</li>
 * </ul>
 */
final class Unattend {
    private Unattend() {}

    /**
     * Microsoft's published generic keys: they only select the edition during setup and do NOT
     * activate Windows.
     */
    static final String KEY_HOME = "YTMG3-N6DKC-DKB77-7M9GH-8HVX7";
    static final String KEY_PRO = "VK7JG-NPHTM-C97JM-9MPGT-3V66T";

    static final class Options {
        boolean arm64;
        /** Disk + edition automation; only for an empty disk. */
        boolean install;
        String productKey = KEY_PRO;
        /** Setup UI language matching the ISO (e.g. ko-KR) or null to ask. */
        String uiLanguage;
        /** Local account automation. */
        boolean localAccount;
        String user = "User";
        String password = "";
    }

    static String xml(Options o) {
        String arch = o.arm64 ? "arm64" : "amd64";
        String comp = "processorArchitecture=\"" + arch + "\" publicKeyToken=\"31bf3856ad364e35\" "
                + "language=\"neutral\" versionScope=\"nonSxS\" "
                + "xmlns:wcm=\"http://schemas.microsoft.com/WMIConfig/2002/State\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"";
        StringBuilder x = new StringBuilder();
        x.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        x.append("<unattend xmlns=\"urn:schemas-microsoft-com:unattend\">\n");

        // ------------------------------------------------------------ windowsPE
        x.append("  <settings pass=\"windowsPE\">\n");
        if (o.install && o.uiLanguage != null) {
            x.append("    <component name=\"Microsoft-Windows-International-Core-WinPE\" ").append(comp).append(">\n")
             .append("      <SetupUILanguage><UILanguage>").append(o.uiLanguage).append("</UILanguage></SetupUILanguage>\n")
             .append("      <InputLocale>0412:00000412</InputLocale>\n")
             .append("      <SystemLocale>ko-KR</SystemLocale>\n")
             .append("      <UILanguage>").append(o.uiLanguage).append("</UILanguage>\n")
             .append("      <UserLocale>ko-KR</UserLocale>\n")
             .append("    </component>\n");
        }
        x.append("    <component name=\"Microsoft-Windows-Setup\" ").append(comp).append(">\n");
        if (o.install) {
            // GPT layout for UEFI: EFI 100MB, MSR 16MB, Windows = rest of the disk
            x.append("      <DiskConfiguration>\n")
             .append("        <Disk wcm:action=\"add\">\n")
             .append("          <DiskID>0</DiskID>\n")
             .append("          <WillWipeDisk>true</WillWipeDisk>\n")
             .append("          <CreatePartitions>\n")
             .append("            <CreatePartition wcm:action=\"add\"><Order>1</Order><Type>EFI</Type><Size>100</Size></CreatePartition>\n")
             .append("            <CreatePartition wcm:action=\"add\"><Order>2</Order><Type>MSR</Type><Size>16</Size></CreatePartition>\n")
             .append("            <CreatePartition wcm:action=\"add\"><Order>3</Order><Type>Primary</Type><Extend>true</Extend></CreatePartition>\n")
             .append("          </CreatePartitions>\n")
             .append("          <ModifyPartitions>\n")
             .append("            <ModifyPartition wcm:action=\"add\"><Order>1</Order><PartitionID>1</PartitionID><Format>FAT32</Format><Label>System</Label></ModifyPartition>\n")
             .append("            <ModifyPartition wcm:action=\"add\"><Order>2</Order><PartitionID>2</PartitionID></ModifyPartition>\n")
             .append("            <ModifyPartition wcm:action=\"add\"><Order>3</Order><PartitionID>3</PartitionID><Format>NTFS</Format><Label>Windows</Label><Letter>C</Letter></ModifyPartition>\n")
             .append("          </ModifyPartitions>\n")
             .append("        </Disk>\n")
             .append("        <WillShowUI>OnError</WillShowUI>\n")
             .append("      </DiskConfiguration>\n")
             .append("      <ImageInstall>\n")
             .append("        <OSImage>\n")
             .append("          <InstallTo><DiskID>0</DiskID><PartitionID>3</PartitionID></InstallTo>\n")
             .append("          <WillShowUI>OnError</WillShowUI>\n")
             .append("        </OSImage>\n")
             .append("      </ImageInstall>\n")
             .append("      <UserData>\n")
             .append("        <AcceptEula>true</AcceptEula>\n")
             .append("        <ProductKey><Key>").append(esc(o.productKey)).append("</Key><WillShowUI>OnError</WillShowUI></ProductKey>\n")
             .append("      </UserData>\n");
        } else {
            x.append("      <UserData><AcceptEula>true</AcceptEula></UserData>\n");
        }
        x.append("      <RunSynchronous>\n")
         .append(labConfig(1, "BypassTPMCheck"))
         .append(labConfig(2, "BypassSecureBootCheck"))
         .append(labConfig(3, "BypassRAMCheck"))
         .append(labConfig(4, "BypassCPUCheck"))
         .append(labConfig(5, "BypassStorageCheck"))
         .append("      </RunSynchronous>\n")
         .append("    </component>\n")
         .append("  </settings>\n");

        if (o.localAccount) {
            String u = esc(o.user == null || o.user.trim().isEmpty() ? "User" : o.user.trim());
            String p = esc(o.password == null ? "" : o.password);

            // ------------------------------------------------------------ specialize
            x.append("  <settings pass=\"specialize\">\n")
             .append("    <component name=\"Microsoft-Windows-Deployment\" ").append(comp).append(">\n")
             .append("      <RunSynchronous>\n")
             .append("        <RunSynchronousCommand wcm:action=\"add\"><Order>1</Order>")
             .append("<Path>reg.exe add \"HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\OOBE\" /v BypassNRO /t REG_DWORD /d 1 /f</Path>")
             .append("</RunSynchronousCommand>\n")
             .append("      </RunSynchronous>\n")
             .append("    </component>\n")
             .append("  </settings>\n");

            // ------------------------------------------------------------ oobeSystem
            x.append("  <settings pass=\"oobeSystem\">\n")
             .append("    <component name=\"Microsoft-Windows-International-Core\" ").append(comp).append(">\n")
             .append("      <InputLocale>0412:00000412</InputLocale>\n")
             .append("      <SystemLocale>ko-KR</SystemLocale>\n")
             .append("      <UserLocale>ko-KR</UserLocale>\n")
             .append("    </component>\n")
             .append("    <component name=\"Microsoft-Windows-Shell-Setup\" ").append(comp).append(">\n")
             .append("      <TimeZone>Korea Standard Time</TimeZone>\n")
             .append("      <OOBE>\n")
             .append("        <HideEULAPage>true</HideEULAPage>\n")
             .append("        <HideOEMRegistrationScreen>true</HideOEMRegistrationScreen>\n")
             .append("        <HideOnlineAccountScreens>true</HideOnlineAccountScreens>\n")
             .append("        <HideWirelessSetupInOOBE>true</HideWirelessSetupInOOBE>\n")
             .append("        <ProtectYourPC>3</ProtectYourPC>\n")
             .append("      </OOBE>\n")
             .append("      <UserAccounts>\n")
             .append("        <LocalAccounts>\n")
             .append("          <LocalAccount wcm:action=\"add\">\n")
             .append("            <Name>").append(u).append("</Name>\n")
             .append("            <DisplayName>").append(u).append("</DisplayName>\n")
             .append("            <Group>Administrators</Group>\n")
             .append("            <Password><Value>").append(p).append("</Value><PlainText>true</PlainText></Password>\n")
             .append("          </LocalAccount>\n")
             .append("        </LocalAccounts>\n")
             .append("      </UserAccounts>\n")
             .append("      <AutoLogon>\n")
             .append("        <Enabled>true</Enabled>\n")
             .append("        <Username>").append(u).append("</Username>\n")
             .append("        <Password><Value>").append(p).append("</Value><PlainText>true</PlainText></Password>\n")
             .append("        <LogonCount>9999999</LogonCount>\n")
             .append("      </AutoLogon>\n")
             .append("    </component>\n")
             .append("  </settings>\n");
        }
        x.append("</unattend>\n");
        return x.toString();
    }

    /** Guess the Setup UI language from Microsoft's ISO file name (e.g. Win11_25H2_Korean_x64.iso). */
    static String languageFromIsoName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.contains("korean")) return "ko-KR";
        if (n.contains("englishinternational")) return "en-GB";
        if (n.contains("english")) return "en-US";
        if (n.contains("japanese")) return "ja-JP";
        if (n.contains("chinesesimplified")) return "zh-CN";
        if (n.contains("chinesetraditional")) return "zh-TW";
        return null;
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
