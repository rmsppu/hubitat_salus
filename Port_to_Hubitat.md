# Overview the hubitat_salus project

## Intent
This project is intended to port the HomeAssistant software to control Salus devices to Hubitat. The origin code currently works.

The new code must use software (Groovy), design, practices, and methods that are compatible with Hubitat Elevation.

The new code must consist of a device driver, a parent application that connects to the Salus Gateway, and child applications that control devices connected to the Salus Gateway.

## Limited Scope
The original project supports multiple Salus Gateway models and multiple Salus devices.

The port to Hubitat must support only the following:
	Salus Universal Gateway (UG) 600
	Salus Wireless Pump Relay Control (4 Zone) model AKL04P
	Salus Wireless Thermostat model AWRT10RF or AS20WRF

References to other Salus devices from the original software should be carried over to the new project in the form of comments, stub (non-functional) devices, etc. in order to allow future development to extend to support other devices.

## Repository

This project lives at:
	https://github.com/rmsppu/hubitat_salus.git

You must make new branch[s] as needed to separate the original code from the Hubitat version.

There must not be any pull requests, commits, or pushes to any other repository.

If source code from another (3rd party) repo will be used in this project, it should be copied into rmsppu/hubitat_salus, with detailed attribution (author name, repo name, etc).

## Persistent Context
### Overview
This file is Overview.md. Each time you begin a session, read this file for an overview of the project and guidelines. This file must not be modified.

### Status
The "Status.md" file will also be read at the start of a new session.

You must frequently update Status.md with sufficient context to resume the task at the same step, even in a new chat session with no prior conversation.

This file is used to maintain context, particularly across multiple sessions or time periods.

During a session, periodically and frequently update the file "Status.md" with information about the current status of the chat, code, and debugging to serve as long-term persistent memory of your progress on the project. This file contains details to supplement Overview.md as needed, and should never be removed. The Status.md file should contain both high-level information and background, but not implementation details for specific tasks (which will be kept in TODO.md, which is considered ephemeral). The Status.md file can be used to maintain a list of future tasks that are not part of the immediate work. These may be tasks mentioned during the chat, sections of code where you observe a need for future improvement, etc.

## Device Driver
See the Home Assistant project in: /home/bergman/Salus_for_Hubitat/salus-it600-client as working example of the python device driver needed to talk to the Salus UGE600 on the local LAN. Note that this MUST be reimplemented in Groovy in order to run on the Hubitat platform.

### TODO
The file "TODO.md" contains a specific list of goals to meet the current task, the status of that task, and a multi-step plan for meeting the task, including testing and verification. You can maintain the TODO.md file, changing it as needed during a task. The TODO.md file should be narrow in scope, referring to the task[s] that are under active work.


Periodically update TODO.md with sufficient context to resume the task at the same step, even in a new chat session with no prior conversation.

## External Documentation, Code Design and Practices

Carefully study the following websites for best practices in developing code for Hubitat:

https://docs2.hubitat.com/en/developer
https://docs2.hubitat.com/en/developer/best-practices
https://github.com/tibrown/HubitatWork/blob/main/Docs/07-Best-Practices/Best-Practices.md
https://community.hubitat.com/t/organizing-code-best-practices-ci-cd/161365/4

### Sample Projects
Carefully review the projects in /home/bergman/Salus_for_Hubitat/Example_Hubitat_Projects for examples of working, production code using Groovy on the Hubitat platform. These can be used as samples to guide the structure, design, coding style, and methods for this port.

## Hubitat Package Manager
Where possible, software must be written with the goal of being managed (install, remove, update) with the Hubitat Package Manager: https://github.com/HubitatCommunity/hubitatpackagemanager

The Hubitat Package Manager repo and "hpm" utility can be found in /home/bergman/Salus_for_Hubitat

## Self-documenting
Use descriptive names for functions and data structures.

Use lots of comments, describing both specifics (ie., the purpose of a single variable) and concepts.

Be self-documenting. Source code must include comments for build an test steps.


## Revision Control Commit Messages
All github commit messages must start with the name of the AI model and agent, as in: 
    git commit -m "Kilo -- MiniMax 2.5: this is the commit message" 

Where the content of the "commit message" is a brief summary of the changes since the previous commit. Commit messages must be formatted to be both human- and machine-readable. The use of semmantic information, such as markdown tags, is encouraged.

# Actions
You may run commands to compile code (clang, gcc, make), debug code (strace, gprof, valgrind, etc), run the executable for testing and check the structure of the project, including reading and altering files with tools like grep, sed, perl ONLY when all files that are being read or written to are in the directory
/home/bergman/Salus_for_Hubitat You do not need confirmation or approval to run those commands.

You may read all files in /home/bergman/Salus_for_Hubitat and subdirectories

You may not alter files in any higher level directory.

## Workspace Directory Structure
For all tasks, only modify files found in the workspace directory /home/bergman/Salus_for_Hubitat

At the beginning of any shell (terminal) session, issue the command:
    cd /home/bergman/Salus_for_Hubitat
and run all future commands from that workspace

Never read or write files to /home/bergman/Desktop.

You may create temporary directories under /home/bergman/Salus_for_Hubitat as needed.
