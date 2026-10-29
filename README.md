# Startup Assistant

An Android app that helps a new founder get started. It has an on-device agent
(Gemini Nano through ML Kit GenAI) whose tools are the app's own AppFunctions.
The same AppFunctions are available to the Gemini app through the Connected
Apps platform.

## Features

| Tab | What it does | Data |
| --- | --- | --- |
| Ask | Chat with an agent that runs on the phone. It calls the app's tools and shows each tool call. Before it records a transaction, it asks you to confirm. | ML Kit GenAI agent (`genai-agent`), Gemini Nano |
| Events | Find events to meet people, meet investors and find office space. Filter by type, text, city and price. | Mock. The Luma API needs Luma Plus and reads only calendars you own, so it cannot search public events. |
| Hiring | Salary and equity ranges by role, level and city. Write a job post, then rewrite it on device (Professional, Friendly, Shorter). See candidates and a hiring checklist. | Mock data. Rewriting uses ML Kit GenAI Rewriting (`genai-rewriting`). |
| Books | Add money in and out (auto-categorized). See cash, monthly burn, runway, expenses by category and a monthly table. | Mock, in memory |

## Agent tools (AppFunctions)

Defined in `app/src/main/java/com/startupassistant/StartupTools.kt`. The KDoc on
each function is the tool description that the model reads.

| Tool | Kind |
| --- | --- |
| `find_events(topic, category, city, freeOnly)` | read |
| `get_salary_range(role, level, location)` | read |
| `find_candidates(role, remoteOnly)` | read |
| `draft_job_post(role, companyName, companyPitch, level, location)` | read |
| `get_runway()` | read |
| `add_transaction(description, amountUsd, date)` | write, needs user confirmation in the app |

## Requirements

- Android Studio with AGP 9.4 and Gradle 9.6 (the wrapper is included)
- For the Ask tab and rewriting: a device with Gemini Nano (for example Pixel 9 or later)
  on Android 14 or later, enrolled in the AICore Developer Preview
- For the Gemini app to call the tools: Android 16 (API 36) or later

The Events, Hiring and Books tabs work on any device with Android 8.0 (API 26) or later.

## Build and run

```
./gradlew :app:installDebug
./gradlew :app:testDebugUnitTest
```

Test a tool without the agent:

```
adb shell cmd app_function list-app-functions --package com.startupassistant
adb shell cmd app_function execute-app-function --package com.startupassistant --function <function id from the list> --parameters '{}'
```

## Replace a mock with a real source

Each feature reads data through an interface in `app/src/main/java/com/startupassistant/data/`:
`EventRepository`, `HiringRepository` and `BooksRepository`. Write a new
implementation and change it in `AppGraph.kt`.
