# Frida Agent Usage & Automation Instruction Guide

This document serves as an operational instruction manual for AI agents, scripts, and automated systems to setup, configure, and execute dynamic binary instrumentation tasks using **Frida**.

---

## 1. Overview & System Requirements

Frida is a dynamic code instrumentation toolkit. It enables agents to inject custom JavaScript logic into running processes across Windows, macOS, GNU/Linux, iOS, and Android.

### Prerequisites
- **Python**: 3.8+ recommended.
- **Operating System**: Windows, macOS, or GNU/Linux.
- **System Privileges**:
  - **Linux**: Enable ptrace for non-child processes: `sysctl kernel.yama.ptrace_scope=0`
  - **Android**: Rooted device or emulator with ADB enabled for full system inspection.

---

## 2. Installation & Environment Setup

### 2.1 Installing Frida CLI & Python Libraries
Agents can install Frida via PyPI using `pip`:

```bash
pip install frida-tools frida
```

Verify the installation:
```bash
frida --version
frida-ps --version
```

### 2.2 Android Device Setup (ADB Workflow)
When an agent automates Frida against an Android target via ADB:

1. **Check Target Architecture**:
   ```bash
   adb shell getprop ro.product.cpu.abi
   # Common outputs: arm64-v8a, x86_64, armeabi-v7a
   ```
2. **Push and Launch Frida Server**:
   Download the matching `frida-server` binary from GitHub Releases, decompress it, and run:
   ```bash
   adb push frida-server /data/local/tmp/
   adb shell "chmod +x /data/local/tmp/frida-server"
   adb shell "su -c '/data/local/tmp/frida-server &'"
   ```
3. **Verify Connection**:
   ```bash
   frida-ps -U
   ```

---

## 3. Execution Modes for Automated Agents

Agents can control Frida via **CLI Mode** (command-line execution) or **Python API Mode** (programmatic integration).

### 3.1 CLI Commands Quick Reference

| Action | Command Syntax |
| :--- | :--- |
| **List Local Processes** | `frida-ps` |
| **List USB Device Processes** | `frida-ps -U` |
| **List Installed USB Apps** | `frida-ps -Ua` or `frida-ps -Uai` |
| **Attach to Running PID/Name** | `frida -U -p <PID> -l <script.js>` |
| **Spawn Package & Inject** | `frida -U -f <package.name> -l <script.js>` |
| **Non-Interactive Spawn** | `frida -U -f <package.name> -l <script.js> --no-pause` |

---

### 3.2 Programmatic Automation via Python API

Using Python bindings allows agents to handle callbacks, process JSON payloads, and manage lifecycle events programmatically.

#### Python Script Template (`agent_runner.py`):
```python
import sys
import frida

def on_message(message, data):
    if message['type'] == 'send':
        print(f"[AGENT MSG]: {message['payload']}")
    elif message['type'] == 'error':
        print(f"[AGENT ERR]: {message['stack']}")

def run_agent_instrumentation(package_name, js_script_path):
    try:
        # Get USB device (Android/iOS) or local device
        device = frida.get_usb_device()
        
        # Spawn application process
        pid = device.spawn([package_name])
        session = device.attach(pid)
        
        # Read JavaScript payload
        with open(js_script_path, "r", encoding="utf-8") as f:
            script_code = f.read()
            
        script = session.create_script(script_code)
        script.on('message', on_message)
        script.load()
        
        # Resume target execution
        device.resume(pid)
        
        # Interact via RPC exports if defined
        if hasattr(script.exports_sync, 'get_status'):
            status = script.exports_sync.get_status()
            print(f"[RPC RESULT]: {status}")
            
    except Exception as e:
        print(f"[EXECUTION FAILURE]: {e}")

if __name__ == "__main__":
    # Example usage: python agent_runner.py com.example.app agent_hook.js
    if len(sys.argv) > 2:
        run_agent_instrumentation(sys.argv[1], sys.argv[2])
```

---

## 4. Writing Frida Instrumentation Scripts (JavaScript)

### 4.1 Java/Android Hooking Template
```javascript
if (Java.available) {
    Java.perform(function () {
        var TargetClass = Java.use("com.example.app.TargetClass");

        // Hooking a method and logging arguments
        TargetClass.checkStatus.implementation = function (arg) {
            console.log("[+] checkStatus called with arg: " + arg);

            // Call original implementation
            var originalResult = this.checkStatus(arg);
            console.log("[+] Original result: " + originalResult);

            // Return custom override if necessary
            return true;
        };
    });
}
```

### 4.2 Native Library Interception (C/C++)
```javascript
var symbolAddress = Module.findExportByName("libtarget.so", "native_function_name");

if (symbolAddress) {
    Interceptor.attach(symbolAddress, {
        onEnter: function (args) {
            console.log("[+] Native function called.");
            console.log("[+] Arg 0: " + args[0]);
        },
        onLeave: function (retval) {
            console.log("[+] Original return value: " + retval);
            // Replace return value if needed
            // retval.replace(ptr("0x1"));
        }
    });
}
```

---

## 5. Agent Operational Rules & Best Practices

1. **Non-Blocking Execution**: Always pass `--no-pause` in CLI or invoke `device.resume(pid)` in Python scripts to prevent the target process from locking.
2. **Robust Exception Handling**: Wrap code inside `try {} catch (e) {}` blocks within JavaScript hooks to prevent application crashes upon unexpected inputs.
3. **Structured RPC Communication**: Prefer using `rpc.exports` in JS scripts so the orchestrating agent can request specific data or invoke targeted hooks dynamically.
4. **Resource Management**: Call `session.detach()` upon completing tests to cleanly unhook target functions and release memory.
