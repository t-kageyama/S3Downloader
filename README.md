# S3 Downloader

A simple command-line downloader for Amazon S3.

Designed for users who simply want to download files from Amazon S3—nothing more, nothing less.

This document is written in English.
Japanese version: [README.ja.md](README.ja.md).

## Contents

1. [Overview](#overview)
2. [Why "Downloader"?](#why-downloader)
3. [Features](#features)
4. [Installation](#installation)
5. [Configuration](#configuration)
6. [Typical Workflow](#typical-workflow)
7. [Commands](#commands)
8. [Planned Features](#planned-features)
9. [Philosophy](#philosophy)
9. [Version History](#version-history)
10. [License](#license)

## Overview

S3 Downloader is a command-line utility designed to download files from Amazon S3 as simply as possible.

Unlike full-featured S3 clients, this project intentionally focuses on downloading files. Its goal is to make common download operations quick, simple, and easy to use.

## Why "Downloader"?

There are already many excellent S3 clients that support uploading, synchronization, file management, and many other advanced features.

This project does not try to replace those tools.

Instead, it focuses on a single task:

Download the requested file from Amazon S3 as quickly and simply as possible.

A typical use case is receiving a file path in an email or chat message and downloading the file immediately from the command line.

The philosophy of this project is simple:

* Keep the interface easy to learn.
* Focus on downloading.
* Leave advanced S3 management to dedicated tools.

## Features

- Interactive command-line interface
- Navigate directories (`cd`, `pwd`)
- List files and directories (`ls`, `lsr`)
- Download files from Amazon S3
- Download by file name or full path
- Command-line download mode (`-d`)
- Lightweight and easy to use

## Installation

Clone the repository.

```bash
git clone ...
```

Build with Maven.

```bash
mvn package
```

The executable JAR will be generated in the `target` directory.

## Configuration

Create a properties file such as:

```properties
accessKeyId=YOUR_ACCESS_KEY_ID
secretAccessKey=YOUR_SECRET_ACCESS_KEY
bucket=your-bucket
region=ap-northeast-1
```

The example uses the AWS Tokyo Region (`ap-northeast-1`).

| Property | Description |
| --- | --- |
| accessKeyId | AWS access key ID |
| secretAccessKey | AWS secret access key |
| bucket | Amazon S3 bucket name |
| region | AWS region |

The application validates all required properties before connecting to Amazon S3.

## Typical Workflow

Suppose your team receives the following email.

```text
The design document has been updated.

Please download the following file.

/DOCUMENTS/SPEC_SHEETS/API_USAGE.pdf
```

There are three common ways to download the file.

1. Interactive mode (change directory first)

```text
$ s3download config.properties
> cd /DOCUMENTS/SPEC_SHEETS
> dl API_USAGE.pdf
```

This is the recommended workflow when downloading multiple files from the same directory.

2. Interactive mode (download using the full path)

```text
$ s3download config.properties
> dl /DOCUMENTS/SPEC_SHEETS/API_USAGE.pdf
```

This is convenient when downloading a single file.

3. Command-line mode

```bash
$ s3download config.properties \
    -d /DOCUMENTS/SPEC_SHEETS/API_USAGE.pdf
```

This is suitable for shell scripts, batch files, or automation.

## Commands

| Command | Description |
|---------|-------------|
| ls | List files and directories |
| lsr | List recursively |
| cd | Change current directory |
| pwd | Show current directory |
| dl | Download a file |
| help | Show help |
| exit | Exit the application |

## Planned Features

The following features may be added in future releases.
Implementation order is not fixed.

- Arguments for `ls` and `lsr`
- Tab completion
- Wildcard support
- Case-insensitive search

## Philosophy

S3 Downloader is intentionally designed to solve one problem well:

**Download the requested file from Amazon S3 quickly and simply.**

Advanced S3 operations should be handled by dedicated S3 management tools.

## Version History

### 1.1.0
- Added command history navigation using the Up and Down arrow keys. Invalid commands and input during file downloads are excluded from the history.
- Fixed an issue where `cd` could incorrectly accept a non-existent directory when its name was a prefix of an existing directory.

### 1.0.0
- Initial release.

## License

This project is licensed under the MIT License.

See the [LICENSE](LICENSE) file for details.