@rem
@rem Gradle startup script for Windows. If gradle-wrapper.jar is missing,
@rem open this project in Android Studio once to regenerate it, or run:
@rem gradle wrapper --gradle-version 8.7
@rem

@if "%DEBUG%"=="" @echo off
setlocal

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%
set APP_NAME=Gradle
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

set JAVA_EXE=java.exe
if defined JAVA_HOME set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
    echo ERROR: %CLASSPATH% not found.
    echo Open this project in Android Studio to regenerate the wrapper jar,
    echo or run "gradle wrapper --gradle-version 8.7" from a machine with Gradle installed.
    exit /b 1
)

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

endlocal
