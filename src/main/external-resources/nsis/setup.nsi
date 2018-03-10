﻿Unicode "true"
ManifestDPIAware true

!pragma warning disable 9000
!pragma warning disable 6010
!pragma warning disable 6040

SetCompressor /SOLID /FINAL lzma
SetCompressorDictSize 64

!include "MUI2.nsh"
!include "FileFunc.nsh"
!include "LogicLib.nsh"
!include "SearchJava.nsh"
!include "Sections.nsh"
!include "serviceLib.nsh"
!include "TextFunc.nsh"
!include "WinVer.nsh"
!include "WordFunc.nsh"
!include "x64.nsh"

; Include the project header file generated by the nsis-maven-plugin
!include "..\..\..\..\target\project.nsh"
!include "${PROJECT_BUILD_DIR}\extra.nsh"

!define INSTALLERMUTEXNAME "$(^Name)"
!define PRODUCT_NAME "${PROJECT_NAME}"
!define PRODUCT_VERSION "v${PROJECT_VERSION_SHORT}"
!define PRODUCT_PUBLISHER "${PROJECT_NAME} Team"
!define PRODUCT_WEB_SITE "${PROJECT_ORGANIZATION_URL}"
!define REG_KEY_UNINSTALL "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${PROJECT_NAME}"
!define REG_KEY_SOFTWARE "SOFTWARE\${PROJECT_NAME}"
!define /utcdate BUILD_YEAR "%Y"

Name "${PROJECT_NAME}"
BrandingText "$CopyLeft"

XPStyle on
InstProgressFlags Smooth colored
SetDatablockOptimize on
SetDateSave on
CRCCheck force
RequestExecutionLevel admin
AllowSkipFiles off
ManifestSupportedOS all ; Left here to remember to add GUI ID in case Windows 11 or above appear before NSIS add their support by default

; Get install folder from registry for updates

InstallDirRegKey HKCU "${REG_KEY_SOFTWARE}" ""

!define MUI_ABORTWARNING
!define MUI_CUSTOMFUNCTION_GUIINIT onGUIInit
!define MUI_UI "${PROJECT_BASEDIR}\src\main\external-resources\third-party\nsis\Contrib\UIs\modern.exe" ; UltraModern.exe
!define MUI_ICON "${PROJECT_BASEDIR}\src\main\resources\images\logo.ico"
!define MUI_UNICON "${PROJECT_BASEDIR}\src\main\resources\images\logo.ico"
!define MUI_PAGE_CUSTOMFUNCTION_SHOW showHiDPI
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_RIGHT
!define MUI_BGCOLOR FFFFFF
!define MUI_HEADER_TRANSPARENT_TEXT
!define MUI_LANGDLL_ALLLANGUAGES
; Remember the installer language (Language selection in dialog settings)
!define MUI_LANGDLL_ALWAYSSHOW
!define MUI_LANGDLL_REGISTRY_ROOT "HKCU"
!define MUI_LANGDLL_REGISTRY_KEY "${REG_KEY_SOFTWARE}"
!define MUI_LANGDLL_REGISTRY_VALUENAME "Installer Language"
!define MUI_WELCOMEPAGE_TITLE_3LINES
!insertmacro MUI_PAGE_WELCOME
!define MUI_CUSTOMFUNCTION_ONMOUSEOVERSECTION downloadJavaPreselection
!define MUI_COMPONENTSPAGE
!define MUI_COMPONENTSPAGE_SMALLDESC
!define MUI_COMPONENTSPAGE_TEXT_TOP " "
!insertmacro MUI_COMPONENTSPAGE_INTERFACE
!insertmacro MUI_PAGEDECLARATION_COMPONENTS
!insertmacro MUI_PAGE_INSTFILES
!define MUI_PAGE_CUSTOMFUNCTION_SHOW showHiDPI
!define MUI_FINISHPAGE_TITLE_3LINES
!define MUI_FINISHPAGE_LINK_COLOR 1E90FF
!define MUI_FINISHPAGE_LINK $(OpenWebSite)
!define MUI_FINISHPAGE_LINK_LOCATION "${PROJECT_ORGANIZATION_URL}"
!define MUI_FINISHPAGE_NOAUTOCLOSE
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_FUNCTION RunDMS
!define MUI_FINISHPAGE_SHOWREADME ""
!define MUI_FINISHPAGE_SHOWREADME_NOTCHECKED
!define MUI_FINISHPAGE_SHOWREADME_TEXT $(DesktopShortcut)
!define MUI_FINISHPAGE_SHOWREADME_FUNCTION CreateDesktopShortcut
!insertmacro MUI_PAGE_FINISH

!define MUI_UNABORTWARNING
!define MUI_PAGE_CUSTOMFUNCTION_SHOW "un.showHiDPI"
!define MUI_UNWELCOMEPAGE_TITLE_3LINES
!insertmacro MUI_UNPAGE_WELCOME
ShowUninstDetails show
!insertmacro MUI_UNPAGE_INSTFILES
!define MUI_PAGE_CUSTOMFUNCTION_SHOW "un.showHiDPI"
!define MUI_UNFINISHPAGE_TITLE_3LINES
!define MUI_UNFINISHPAGE_NOAUTOCLOSE
!insertmacro MUI_UNPAGE_FINISH

!include setupLanguages.nsh

; Reserve Files

  ;If you are using solid compression, files that are required before
  ;the actual installation should be stored first in the data block,
  ;because this will make your installer start faster.

!insertmacro MUI_RESERVEFILE_LANGDLL

Var CopyLeft
Var FirewallStatus
Var RAM

; ComponentText "Select the components you want to install."

Section /o "-Cleaning" sec0
	ReadENVStr $R1 "ALLUSERSPROFILE"
	RMDir /r $R1\${PROJECT_NAME_CAMEL}
	RMDir /r $TEMP\fontconfig
	RMDir /r $LOCALAPPDATA\fontconfig
	RMDir /r $INSTDIR
SectionEnd

Section "!$(SectionServer)" sec1
	SectionIn RO

	SetOutPath "$INSTDIR"
	SetOverwrite on

	CreateDirectory "$INSTDIR\plugins"
	AccessControl::GrantOnFile "$INSTDIR\plugins" "(S-1-5-32-545)" "FullAccess"
	File /r /x "*.conf" /x "*.zip" /x "*.rar" /x "*.7z" /x "*.dll" /x "third-party" "${PROJECT_BASEDIR}\src\main\external-resources\plugins"
	File /r "${PROJECT_BASEDIR}\src\main\external-resources\documentation"
	File /r "${PROJECT_BASEDIR}\src\main\external-resources\renderers"
	File /r /x "ffmpeg*.exe" /x "avisynth" /x "MediaInfo64.dll" "${PROJECT_BASEDIR}\target\bin\win32"
	File "${PROJECT_BUILD_DIR}\${PROJECT_NAME_SHORT}.exe"
	File "${PROJECT_BASEDIR}\src\main\external-resources\${PROJECT_NAME_SHORT}.bat"
	File /r "${PROJECT_BASEDIR}\src\main\external-resources\web"
	File "${PROJECT_BUILD_DIR}\${PROJECT_ARTIFACT_ID}.jar"
	; File "${PROJECT_BASEDIR}\CHANGELOG.txt"
	File "${PROJECT_BASEDIR}\README.md"
	File "${PROJECT_BASEDIR}\README.txt"
	File "${PROJECT_BASEDIR}\LICENSE.txt"
	File "${PROJECT_BASEDIR}\src\main\external-resources\logback.xml"
	File "${PROJECT_BASEDIR}\src\main\external-resources\logback.headless.xml"
	File /oname=${PROJECT_ARTIFACT_ID}.ico "${PROJECT_BASEDIR}\src\main\resources\images\logo.ico"
	File "${PROJECT_BASEDIR}\src\main\external-resources\DummyInput.ass"
	File "${PROJECT_BASEDIR}\src\main\external-resources\DummyInput.jpg"

	SetOutPath "$INSTDIR\win32\service"
	File "${PROJECT_BASEDIR}\src\main\external-resources\third-party\wrapper\*.*"

	SetOutPath "$INSTDIR\win32"
	File "${PROJECT_BASEDIR}\src\main\external-resources\lib\ctrlsender\ctrlsender.exe"

	; The user may have set the installation folder as the profile folder, so we can't clobber this
	SetOutPath "$INSTDIR"
	SetOverwrite off
	File "${PROJECT_BASEDIR}\src\main\external-resources\${PROJECT_NAME_SHORT}.conf"
	File "${PROJECT_BASEDIR}\src\main\external-resources\WEB.conf"
	File "${PROJECT_BASEDIR}\src\main\external-resources\ffmpeg.webfilters"
	File "${PROJECT_BASEDIR}\src\main\external-resources\VirtualFolders.conf"

	; Store install folder
	WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "" $INSTDIR

	; Create uninstaller
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "DisplayName" "${PROJECT_NAME}"
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "DisplayIcon" "$INSTDIR\${PROJECT_ARTIFACT_ID}.ico"
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "DisplayVersion" "${PROJECT_VERSION}"
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "Publisher" "${PROJECT_ORGANIZATION_NAME}"
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "URLInfoAbout" "${PROJECT_ORGANIZATION_URL}"
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "UninstallString" '"$INSTDIR\uninst.exe"'
	WriteRegDWORD HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "NoModify" 0x00000001
	WriteRegDWORD HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "NoRepair" 0x00000001
	WriteRegStr HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}" "FirewallSettings" "$FirewallStatus"

	${GetSize} "$INSTDIR" "/S=0B" $0 $1 $2
	IntFmt $0 "0x%08x" $0 ; https://msdn.microsoft.com/en-us/library/windows/desktop/ms647550(v=vs.85).aspx
	WriteRegDWORD HKLM "${REG_KEY_UNINSTALL}" "EstimatedSize" "$0"

	SetOutPath "$INSTDIR"
	SetOverwrite on
	WriteUninstaller "$INSTDIR\uninst.exe"

	ReadENVStr $R0 "ALLUSERSPROFILE"
	SetOutPath "$R0\${PROJECT_NAME_CAMEL}"
	CreateDirectory "$R0\${PROJECT_NAME_CAMEL}\data"
	AccessControl::GrantOnFile "$R0\${PROJECT_NAME_CAMEL}" "(S-1-5-32-545)" "FullAccess"
;	AccessControl::GrantOnFile "$R0\${PROJECT_NAME_CAMEL}\data" "(BU)" "FullAccess"
	File "${PROJECT_BASEDIR}\src\main\external-resources\${PROJECT_NAME_SHORT}.conf"
	File "${PROJECT_BASEDIR}\src\main\external-resources\WEB.conf"
	File "${PROJECT_BASEDIR}\src\main\external-resources\ffmpeg.webfilters"
	File "${PROJECT_BASEDIR}\src\main\external-resources\VirtualFolders.conf"
SectionEnd

Section $(SectionShortcuts) sec7
	SetShellVarContext all
	CreateDirectory "$SMPROGRAMS\${PROJECT_NAME}"
	CreateShortCut "$SMPROGRAMS\${PROJECT_NAME}\${PROJECT_NAME_SHORT} (Select Profile).lnk" "$INSTDIR\${PROJECT_NAME_SHORT}.exe" "profiles" "" "" SW_SHOWNORMAL CONTROL|SHIFT|P "Select a profile"
	CreateShortCut "$SMPROGRAMS\${PROJECT_NAME}\${PROJECT_NAME}.lnk" "$INSTDIR\${PROJECT_NAME_SHORT}.exe" "" "" "" SW_SHOWNORMAL ALT|F9 "Start ${PROJECT_NAME}"
	${If} ${IsWinXP}
		CreateShortCut "$SMPROGRAMS\${PROJECT_NAME}\Uninstall.lnk" "$INSTDIR\uninst.exe" "" "" "" SW_SHOWNORMAL CONTROL|SHIFT|U "Uninstall ${PROJECT_NAME}"
	${EndIf}
SectionEnd

Section "-32-bit" sec11
	SetOverwrite on
	SetOutPath "$INSTDIR\win32"
	File "${PROJECT_BASEDIR}\target\bin\win32\ffmpeg.exe"
	LockedList::AddModule "$INSTDIR\win32\MediaInfo.dll"
SectionEnd

Section "-64-bit" sec12
	SetOverwrite on
	SetOutPath "$INSTDIR\win32"
	File "${PROJECT_BASEDIR}\target\bin\win32\ffmpeg64.exe"
	LockedList::AddModule "$INSTDIR\win32\MediaInfo.dll"
	File "${PROJECT_BASEDIR}\target\bin\win32\MediaInfo64.dll"
	LockedList::AddModule "$INSTDIR\win32\MediaInfo64.dll"
SectionEnd

Section /o "-XP" sec13
	SetOverwrite on
	SetOutPath "$INSTDIR\win32"
	File /r "${PROJECT_BASEDIR}\src\main\external-resources\lib\winxp"
SectionEnd

Section /o $(SectionRemovePrevious) sec5
SectionEnd

Section /o $(SectionWindowsFirewall) sec2
	StrCpy $FirewallStatus "1" ; Will be used later by the uninstaller

	${IfNot} ${IsWinXP}
		nsExec::Exec 'netsh advfirewall firewall add rule name="Digital Media Server - Incoming port TCP 1900/5001/9001" action=allow description="Incoming on port TCP 1900/5001/9001" dir=in enable=yes profile=private,domain protocol=tcp localport=1900,5001,9001'
		nsExec::Exec 'netsh advfirewall firewall add rule name="Digital Media Server - Incoming port UDP 1900"  action=allow description="Incoming on port UDP 1900" dir=in enable=yes profile=private,domain protocol=udp localport=1900'
	${Else}
		nsExec::Exec 'netsh firewall set multicastbroadcastresponse mode=enable profile=standard'
		nsExec::Exec 'netsh firewall set multicastbroadcastresponse mode=enable profile=domain'
		nsExec::Exec 'netsh firewall add portopening protocol=tcp port=5001 name="Digital Media Server - TCP 5001" mode=enable profile=standard'
		nsExec::Exec 'netsh firewall add portopening protocol=tcp port=9001 name="Digital Media Server - TCP 9001" mode=enable profile=standard'
		nsExec::Exec 'netsh firewall add portopening protocol=tcp port=5001 name="Digital Media Server - TCP 5001" mode=enable profile=domain'
		nsExec::Exec 'netsh firewall add portopening protocol=tcp port=9001 name="Digital Media Server - TCP 9001" mode=enable profile=domain'
		nsExec::Exec 'netsh firewall add portopening protocol=All port=1900 name="Digital Media Server - TCP/UDP 1900" mode=enable profile=standard'
		nsExec::Exec 'netsh firewall add portopening protocol=all port=1900 name="Digital Media Server - TCP/UDP 1900" mode=enable profile=domain'
	${EndIf}
	; Future Windows 10 or later versions should not accept anymore "netsh" use for the firewall configuration, so a powershell script or plugin or code should be used
	; To check if other firewalls are blocking ports: netstat -ano | findstr -i "5001" or portqry.exe -n x.x.x.x -e 5001
SectionEnd

Section /o "${U+031F} Java Runtime" sec3 ; http://www.oracle.com/technetwork/java/javase/windows-diskspace-140460.html
	${If} ${AtLeastWinVista}
		inetc::get /NOCANCEL /CONNECTTIMEOUT 30 /SILENT /WEAKSECURITY /NOCOOKIES /TOSTACK "https://lv.binarybabel.org/catalog-api/java/jdk8.txt?p=downloads.exe" "" /END
		Pop $1
		Pop $0
		${WordReplaceS} "$0" "download" "edelivery" "+1" $0
		${WordReplaceS} "$0" "jdk-" "jre-" "+1" $0
		${IfNot} ${RunningX64}
			${WordReplaceS} "$0" "-x64" "-i586" "+1" $0
		${EndIf}
		${WordFind} "$0" "/" "-1}" $1
	${EndIf}
	${If} ${IsWinXP}
	${AndIfNot} ${RunningX64}
		; jre-7u80-windows-i586.exe
		; http://javadl.sun.com/webapps/download/AutoDL?BundleId=106307
		StrCpy $0 "javadl.oracle.com/webapps/download/AutoDL?BundleId=227550_e758a0de34e24606bca991d704f6dcbf"
		StrCpy $1 "jre-8u151-windows-i586.exe"
	${EndIf}
	${If} ${IsWinXP}
	${AndIf} ${RunningX64}
		; jre-7u80-windows-x64.exe
		; http://javadl.sun.com/webapps/download/AutoDL?BundleId=106309
		StrCpy $0 "javadl.oracle.com/webapps/download/AutoDL?BundleId=227552_e758a0de34e24606bca991d704f6dcbf"
		StrCpy $1 "jre-8u151-windows-x64.exe"
	${EndIf}
	${WordReplaceS} $(Downloading) "%s" "Oracle Java 8" "+1" $2
	inetc::get /WEAKSECURITY /RESUME "" /CONNECTTIMEOUT 30 /MODERNPOPUP "$1" /CAPTION "$2" /QUESTION $(ConfirmCancel) /TRANSLATE $(DownloadingFile) $(Downloaded) $(TimeRemaining) $(Speed) $(CancelButton) /USERAGENT "Mozilla/5.0 (Windows NT 6.3; rv:48.0) Gecko/20100101 Firefox/48.0" /HEADER "Cookie: oraclelicense=accept-securebackup-cookie" /NOCOOKIES "$0" "$PLUGINSDIR\$1" /END
	Pop $0
	StrCmpS $0 "OK" JavaDownloadOK
	${WordReplaceS} $(DownloadError) "%s" $0 "+1" $0
	MessageBox MB_ICONEXCLAMATION $0
	Goto End

	JavaDownloadOK:
		ExecWait "$PLUGINSDIR\$1" ; '"$PLUGINSDIR\$1 /s /v$\"/qn ADDLOCAL=ALL REBOOT=Suppress /L C:\setup.log$\""'

	End:
SectionEnd

SectionGroup $(SectionHeapSize) sec4
		; http://www.oracle.com/technetwork/java/hotspotfaq-138619.html#gc_heap_32bit
	Section /o "512 MB" sec41
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "512M"
	SectionEnd

	Section /o "768 MB" sec42
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "768M"
	SectionEnd

	Section /o "1280 MB" sec43
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "1280M"
	SectionEnd

	Section /o "1536 MB" sec44
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "1536M"
	SectionEnd

	Section /o "4096 MB" sec46
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "4096M"
	SectionEnd

	Section /o "6144 MB" sec47
		WriteRegStr HKCU "${REG_KEY_SOFTWARE}" "HeapMem" "6144M"
	SectionEnd
SectionGroupEnd

Section /o "AviSynth" sec6
	; https://forum.doom9.org/showthread.php?t=148782
	; https://nightly.mpc-hc.org/mpc-hc_apps/vsfilter/
	; A more up to date sofware with a 64-bit version and multithreading support like AviSynth+ or VapourSynth could replace AviSynth and be directly downloaded from their website:
	; https://github.com/pinterf/AviSynthPlus/releases
	; https://github.com/vapoursynth/vapoursynth/releases
	SetOverwrite on
	SetOutPath "$INSTDIR\win32\avisynth"
	File "${PROJECT_BASEDIR}\target\bin\win32\avisynth\avisynth.exe"
	ExecWait "$INSTDIR\win32\avisynth\avisynth.exe"
SectionEnd

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
	!insertmacro MUI_DESCRIPTION_TEXT ${sec1} $(SectionDescriptionServer)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec7} $(SectionDescriptionShortcuts)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec5} $(SectionDescriptionRemovePrevious)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec2} $(SectionDescriptionWindowsFirewall)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec3} $(SectionDescriptionInstallJava)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec4} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec41} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec42} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec43} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec44} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec46} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec47} $(SectionDescriptionHeapSize)
	!insertmacro MUI_DESCRIPTION_TEXT ${sec6} $(SectionDescriptionAviSynth)
!insertmacro MUI_FUNCTION_DESCRIPTION_END

Function .onSelChange
	SectionGetFlags ${sec3} $1
	${If} $1 != 0
		FindWindow $1 "#32770" "" $HWNDPARENT
		GetDlgItem $1 $1 1023 ; Required space control
		ShowWindow $1 ${SW_HIDE}
	${Else}
		FindWindow $1 "#32770" "" $HWNDPARENT
		GetDlgItem $1 $1 1023
		ShowWindow $1 ${SW_SHOW}
	${EndIf}

	
	SectionGetFlags ${sec5} $1
	${If} $1 != 0
		SectionSetFlags ${sec0} ${SF_SELECTED}
	${EndIf}

	; Heap memory size section group radio buttons
	StrCpy $2 0

	OnlyOneRadioButtonSelected:
		Push $R2
		StrCpy $R2 ${SF_SELECTED}
		SectionGetFlags ${sec41} $0
		IntOp $R2 $R2 & $0
		SectionGetFlags ${sec42} $0
		IntOp $R2 $R2 & $0
		SectionGetFlags ${sec43} $0
		IntOp $R2 $R2 & $0
		SectionGetFlags ${sec44} $0
		IntOp $R2 $R2 & $0
		SectionGetFlags ${sec46} $0
		IntOp $R2 $R2 & $0
		SectionGetFlags ${sec47} $0
		IntOp $R2 $R2 & $0

		StrCmp $R2 0 NotAllSelected
			SectionSetFlags ${sec41} 0
			SectionSetFlags ${sec42} 0
			SectionSetFlags ${sec43} 0
			SectionSetFlags ${sec44} 0
			SectionSetFlags ${sec46} 0
			SectionSetFlags ${sec47} 0

	NotAllSelected:
		Pop $R2
			!insertmacro StartRadioButtons $R4
			!insertmacro RadioButton ${sec41}
			!insertmacro RadioButton ${sec42}
			!insertmacro RadioButton ${sec43}
			!insertmacro RadioButton ${sec44}
			!insertmacro RadioButton ${sec46}
			!insertmacro RadioButton ${sec47}
		!insertmacro EndRadioButtons

	StrCmp $2 0 0 +3
	StrCpy $2 1
	Goto OnlyOneRadioButtonSelected
FunctionEnd

Function RunDMS ; http://mdb-blog.blogspot.ru/2013/01/nsis-lunch-program-as-user-from-uac.html
	; Run program through explorer.exe to de-evaluate user from admin level to regular one.
	Exec '"$WINDIR\explorer.exe" "$INSTDIR\${PROJECT_NAME_SHORT}.exe"'
FunctionEnd

Function CreateDesktopShortcut
	CreateShortCut "$DESKTOP\${PROJECT_NAME}.lnk" "$INSTDIR\${PROJECT_NAME_SHORT}.exe" "" "" "" SW_SHOWNORMAL ALT|F9 "Start ${PROJECT_NAME}"
FunctionEnd

Function .onInit
	LogSet on ; http://nsis.sourceforge.net/Special_Builds

	${If} ${RunningX64}
		SetRegView 64
		StrCpy "$INSTDIR" "$PROGRAMFILES64\${PROJECT_NAME}"
	${Else}
		StrCpy "$INSTDIR" "$PROGRAMFILES\${PROJECT_NAME}"
	${EndIf}

	InitPluginsDir

	StrCpy $CopyLeft "${U+00A9} ${BUILD_YEAR} ${PRODUCT_NAME} ${PRODUCT_VERSION}                                                 Nullsoft Install System ${NSIS_VERSION}"
	${If} ${AtLeastWinVista}
	${AndIf} ${AtMostWin7}
		StrCpy $CopyLeft "${U+00A9} ${BUILD_YEAR} ${PRODUCT_NAME} ${PRODUCT_VERSION}"
	${EndIf}

	BringToFront ; http://nsis.sourceforge.net/Allow_only_one_installer_instance
	!ifndef NSIS_PTR_SIZE & SYSTYPE_PTR
		!define SYSTYPE_PTR i ; NSIS v2.4x
	!else
		!define /ifndef SYSTYPE_PTR p ; NSIS v3.0+
	!endif
	System::Call 'kernel32::CreateMutex(${SYSTYPE_PTR}0, i1, t"${INSTALLERMUTEXNAME}")?e'
	Pop $0
	IntCmpU $0 183 0 launch launch ; ERROR_ALREADY_EXISTS
		StrLen $0 "$(^SetupCaption)"
		IntOp $0 $0 + 1 ; GetWindowText count includes \0
		StrCpy $1 "" ; Start FindWindow with NULL
		loop:
			FindWindow $1 "#32770" "" "" $1
			StrCmp 0 $1 notfound
			System::Call 'user32::GetWindowText(${SYSTYPE_PTR}r1, t.r2, ir0)'
			StrCmp $2 "$(^SetupCaption)" 0 loop
			SendMessage $1 0x112 0xF120 0 /TIMEOUT=2000 ; WM_SYSCOMMAND:SC_RESTORE to restore the window if it is minimized
			System::Call "user32::SetForegroundWindow(${SYSTYPE_PTR}r1)"
		notfound:
			Abort
	launch:

	${If} ${RunningX64}
		SectionSetFlags ${sec11} ${SECTION_OFF}
		SectionSetFlags ${sec12} ${SF_SELECTED}
	${Else}
		SectionSetFlags ${sec11} ${SF_SELECTED}
		SectionSetFlags ${sec12} ${SECTION_OFF}
	${EndIf}

	${IfNot} ${AtLeastWinXP}
		MessageBox MB_OK|MB_ICONEXCLAMATION $(TooLowVersion)
		Quit
	${EndIf}
	${If} ${IsWinXP}
	${AndIfNot} ${AtLeastServicePack} 3
		MessageBox MB_OK|MB_ICONEXCLAMATION $(TooLowSP)
		Quit
	${EndIf}

	${If} ${IsWinXP}
		SectionSetFlags ${sec13} ${SF_SELECTED}
	${EndIf}

	!insertmacro MUI_LANGDLL_DISPLAY
	StrCmp $LANGUAGE "1033" memoryTest
	System::Call 'kernel32::GetLocaleInfo(i $LANGUAGE, i 89, t .r0, i 9'
	System::Call 'kernel32::GetLocaleInfo(i $LANGUAGE, i 90, t .r2, i 9'
	StrCpy $3 "$0-$2"
	ClearErrors
	${Locate} "${PROJECT_BASEDIR}\src\main\external-resources\nsis\I18N" "/L=F /G=0 /M=setup$3*.nsh" "LanguageFileSearch"
	IfErrors 0 +3
	StrCpy $3 $0
	Goto -4
	${LineFind} "$R0" "/NUL" "15:19" "LanguageFileCheck"
	StrCmp $R1 $R3 +2
	StrCpy $LANGUAGE "1033"

	memoryTest: ; Get the amount of total physical memory
	; https://nsis-dev.github.io/NSIS-Forums/html/t-242501.html
	; https://msdn.microsoft.com/fr-fr/library/windows/desktop/aa366589(v=vs.85).aspx
	System::Alloc 64
	Pop $1
	System::Call "*$1(i64)"
	System::Call "Kernel32::GlobalMemoryStatusEx(i r1)"
	System::Call "*$1(i.r2, i.r3, l.r4, l.r5, l.r6, l.r7, l.r8, l.r9, l.r10)"
	System::Free $1
	System::Int64Op $4 / 1048576 ; convert from bytes to Mbytes
	Pop $4

	; Choose the maximum Java memory heap size
	${If} $4 > 4000
		SectionSetFlags ${sec43} ${SF_SELECTED}
		StrCpy $R4 ${sec43}
	${Else}
		SectionSetFlags ${sec42} ${SF_SELECTED}
		StrCpy $R4 ${sec42}
	${EndIf}
	StrCpy $RAM $4

	${SearchJava}

	${If} $Java64bit != "64"
		SectionSetText ${sec47} ""
		SectionSetText ${sec46} ""
	${ElseIf} $Java64bit == "64"
		IntCmpU $RAM 6000 +2 0 +2
		SectionSetText ${sec46} ""
		IntCmpU $RAM 8000 +2 0 +2
		SectionSetText ${sec47} ""
	${EndIf}

	${If} ${RunningX64}
		ReadRegStr $0 HKLM "SOFTWARE\Wow6432Node\Microsoft\Windows\CurrentVersion\Uninstall\AviSynth" "DisplayVersion"
	${Else}
		ReadRegStr $0 HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\AviSynth" "DisplayVersion"
	${EndIf}
	${If} $0 S== "2.6.0 MT"
		SectionSetText ${sec6} ""
	${EndIf}

	${If} $DownloadJava == "1"
		SectionSetFlags ${sec3} 1
	${EndIf}

	SetOutPath "$PLUGINSDIR\Header"
	SetOverwrite on
	File "Images\InstallerHeader@192.bmp"
	File "Images\InstallerHeader@144.bmp"
	File "Images\InstallerHeader@120.bmp"
	File "Images\InstallerHeader@96.bmp"
	SetOutPath "$PLUGINSDIR\Wizard"
	File "Images\Installer@192.bmp"
	File "Images\Installer@144.bmp"
	File "Images\Installer@120.bmp"
	File "Images\Installer@96.bmp"
FunctionEnd

Function LanguageFileSearch
	StrCpy $R0 $R9
	StrCpy $0 StopLocate
	Push $0
FunctionEnd

Function LanguageFileCheck
	StrCpy $R1 $R9
	${WordFind} "$R9" "$\"$\"" "+1}" $R3
	StrCmp $R9 $R3 +2
	StrCpy $0 StopLineFind
	Push $0
FunctionEnd

Function onGUIInit
	Aero::Apply ; Apply Aero if available
FunctionEnd

Function downloadJavaPreselection
	SectionGetFlags ${sec3} $1
	${If} $DownloadJava == "1"
	${AndIf} $1 != 0
		FindWindow $1 "#32770" "" $HWNDPARENT
		GetDlgItem $1 $1 1023 ; Required space control
		ShowWindow $1 ${SW_HIDE}
	${EndIf}
FunctionEnd

Function un.onInit
	!insertmacro MUI_UNGETLANGUAGE

	ReadRegStr $0 HKLM "${REG_KEY_UNINSTALL}" "FirewallSettings"
	Pop $0
	StrCmp $0 "" noNeed
	${IfNot} ${IsWinXP}
		nsExec::Exec 'netsh advfirewall firewall delete rule name="Digital Media Server - Incoming port TCP 1900/5001/9001"'
		nsExec::Exec 'netsh advfirewall firewall delete rule name="Digital Media Server - Incoming port UDP 1900"'
	${ElseIf} ${IsWinXP}
		nsExec::Exec 'netsh firewall set portopening protocol=tcp port=5001 mode=disable profile=all'
		nsExec::Exec 'netsh firewall set portopening protocol=tcp port=9001 mode=disable profile=all'
		nsExec::Exec 'netsh firewall set portopening protocol=all port=1900 mode=disable profile=all'
	${EndIf}

	noNeed:
FunctionEnd

Function showHiDPI
	SysCompImg::GetSysDpi ; http://forums.winamp.com/showthread.php?t=443754
	${If} $0 > 144
	StrCpy $R6 "InstallerHeader@192.bmp"
	StrCpy $R7 "Installer@192.bmp"
	${ElseIf} $0 > 120
	StrCpy $R6 "InstallerHeader@144.bmp"
	StrCpy $R7 "Installer@144.bmp"
	${ElseIf} $0 > 96
	StrCpy $R6 "InstallerHeader@120.bmp"
	StrCpy $R7 "Installer@120.bmp"
	${Else}
	StrCpy $R6 "InstallerHeader@96.bmp"
	StrCpy $R7 "Installer@96.bmp"
	${EndIf}
	SysCompImg::SetCustom "$PLUGINSDIR\Header\$R6" ; SetClassic, SetFlat, SetThemed
	SysCompImg::SetCustom "$PLUGINSDIR\Wizard\$R7"
	${NSD_SetImage} $mui.WelcomePage.Image "$PLUGINSDIR\Wizard\$R7" $mui.WelcomePage.Image.Bitmap
	${NSD_SetImage} $mui.FinishPage.Image "$PLUGINSDIR\Wizard\$R7" $mui.FinishPage.Image.Bitmap
	SetBrandingImage /IMGID=1046 "$PLUGINSDIR\Header\$R6"
FunctionEnd

Function un.showHiDPI
	SetOutPath "$PLUGINSDIR\Header"
	SetOverwrite on
	File "Images\InstallerHeader@192.bmp"
	File "Images\InstallerHeader@144.bmp"
	File "Images\InstallerHeader@120.bmp"
	File "Images\InstallerHeader@96.bmp"
	SetOutPath "$PLUGINSDIR\Wizard"
	File "Images\Uninstaller@192.bmp"
	File "Images\Uninstaller@144.bmp"
	File "Images\Uninstaller@120.bmp"
	File "Images\Uninstaller@96.bmp"

	SysCompImg::GetSysDpi ; http://forums.winamp.com/showthread.php?t=443754
	${If} $0 > 144
	StrCpy $R6 "InstallerHeader@192.bmp"
	StrCpy $R7 "Uninstaller@192.bmp"
	${ElseIf} $0 > 120
	StrCpy $R6 "InstallerHeader@144.bmp"
	StrCpy $R7 "Uninstaller@144.bmp"
	${ElseIf} $0 > 96
	StrCpy $R6 "InstallerHeader@120.bmp"
	StrCpy $R7 "Uninstaller@120.bmp"
	${Else}
	StrCpy $R6 "InstallerHeader@96.bmp"
	StrCpy $R7 "Uninstaller@96.bmp"
	${EndIf}
	SysCompImg::SetCustom "$PLUGINSDIR\Header\$R6" ; SetClassic, SetFlat, SetThemed
	SysCompImg::SetCustom "$PLUGINSDIR\Wizard\$R7"
	${NSD_SetImage} $mui.WelcomePage.Image "$PLUGINSDIR\Wizard\$R7" $mui.WelcomePage.Image.Bitmap
	${NSD_SetImage} $mui.FinishPage.Image "$PLUGINSDIR\Wizard\$R7" $mui.FinishPage.Image.Bitmap
	SetBrandingImage /IMGID=1046 "$PLUGINSDIR\Header\$R6"
FunctionEnd

Section Uninstall
	SetShellVarContext all
	SetOutPath $TEMP ; Make sure $InstDir is not the current folder so we can remove it
	ClearErrors
	FileOpen $0 "$INSTDIR\install.log" r
	IfErrors 0 looping
	MessageBox MB_ICONEXCLAMATION|MB_YESNO 'Cannot open the file "install.log".$\r$\nThe uninstaller cannot work correctly.$\r$\nDo you want force the uninstall anyway?' IDYES +2
	Quit
	ReadENVStr $R1 "ALLUSERSPROFILE"
	Delete /REBOOTOK "$DESKTOP\${PROJECT_NAME}.lnk"
	RMDir /r /REBOOTOK $R1\${PROJECT_NAME_CAMEL}
	RMDir /r /REBOOTOK $TEMP\fontconfig
	RMDir /r /REBOOTOK $LOCALAPPDATA\fontconfig
	RMDir /r /REBOOTOK $INSTDIR
	RMDir /r /REBOOTOK "$SMPROGRAMS\${PROJECT_NAME}"
	Goto final

	looping:
		ClearErrors
		FileReadUTF16LE $0 $1
		IfErrors EOF
		${WordFindS} "$1" "File: wrote" "E+1" $3
		IfErrors 0 secondPass
		ClearErrors
		${WordFindS} "$1" "CreateShortcut: out:" "E+1" $3
		IfErrors looping
		secondPass: ${WordFind2X} "$1" "$\"" "$\"" "E+1" $3
		IfErrors looping
		Delete /REBOOTOK $3
		${WordFind} "$3" "\" "-1{" $4
		RMDir /REBOOTOK $4
		Goto looping

	EOF:
		FileClose $0
		Delete /REBOOTOK "$INSTDIR\install.log"
		Delete /REBOOTOK "$INSTDIR\uninst.exe"
		RMDir /REBOOTOK "$INSTDIR\documentation"
		RMDir /REBOOTOK "$INSTDIR\web\bump"
		RMDir /REBOOTOK "$INSTDIR\web"
		RMDir /REBOOTOK "$INSTDIR\win32\fonts\conf.avail"
		RMDir /REBOOTOK "$INSTDIR\win32\fonts"
		RMDir /REBOOTOK "$INSTDIR\win32\service"
		RMDir /REBOOTOK "$INSTDIR\win32"
		RMDir /REBOOTOK "$INSTDIR"

	final:
		DeleteRegKey HKEY_LOCAL_MACHINE "${REG_KEY_UNINSTALL}"
		DeleteRegKey HKCU "${REG_KEY_SOFTWARE}"

	!insertmacro SERVICE "running" "${PROJECT_NAME}" ""
	Pop $0
	StrCmpS $0 "false" Done

	ServiceStop:
		!insertmacro SERVICE "stop" "${PROJECT_NAME}" ""
		Pop $0
		StrCmpS $0 "false" 0 ServiceDelete
		MessageBox MB_ABORTRETRYIGNORE|MB_ICONEXCLAMATION $(ServiceStopError) IDIGNORE ServiceDelete IDRETRY ServiceStop
		Abort

	ServiceDelete:
		!insertmacro SERVICE "delete" "${PROJECT_NAME}" ""
		Pop $0
		StrCmpS $0 "false" 0 Done
		MessageBox MB_ABORTRETRYIGNORE|MB_ICONSTOP $(ServiceUninstallError) IDIGNORE Done IDRETRY ServiceDelete
		Abort

	Done:
SectionEnd
