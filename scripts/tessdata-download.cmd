@echo off
REM Downloads the Tesseract language models document-service needs for OCR (scans / photos -> text).
REM   TESSDATA_PATH - target folder (default C:\Softwares\tessdata); document-service reads the same variable.
REM Models come from the official tessdata_fast set (smaller, ~2x faster than tessdata_best, accuracy within ~1%).
setlocal
if "%TESSDATA_PATH%"=="" set TESSDATA_PATH=C:\Softwares\tessdata
if not exist "%TESSDATA_PATH%" mkdir "%TESSDATA_PATH%"
set BASE=https://github.com/tesseract-ocr/tessdata_fast/raw/main
for %%L in (eng ara osd) do (
  if exist "%TESSDATA_PATH%\%%L.traineddata" (
    echo %%L.traineddata already present
  ) else (
    echo Downloading %%L.traineddata ...
    curl -sSL -o "%TESSDATA_PATH%\%%L.traineddata" %BASE%/%%L.traineddata || exit /b 1
  )
)
echo.
echo tessdata ready in %TESSDATA_PATH%
dir /b "%TESSDATA_PATH%"
