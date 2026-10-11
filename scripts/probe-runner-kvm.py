"""Диагностика KVM в Linux-контейнере CI без SDK, эмулятора и установки пакетов."""

import json
import os
import platform
from pathlib import Path


def probe():
    # Наличие CPU-флагов само по себе не доказывает доступность KVM контейнеру.
    cpuinfo = Path('/proc/cpuinfo')
    cpu_text = cpuinfo.read_text() if cpuinfo.exists() else ''
    result = {
        'system': platform.system(),
        'architecture': platform.machine(),
        'cpu_virtualization_flag': any(flag in cpu_text.split() for flag in ('vmx', 'svm')),
        'device_exists': Path('/dev/kvm').exists(),
        'device_readable': os.access('/dev/kvm', os.R_OK),
        'device_writable': os.access('/dev/kvm', os.W_OK),
        'api_version': None,
        'vm_created': False,
        'kvm_usable': False,
    }
    if platform.system() != 'Linux':
        result['reason'] = 'linux_required'
        return result
    if not result['device_exists']:
        result['reason'] = 'device_missing'
        return result

    # Открываем устройство и создаём пустую VM: это сильнее проверки прав файла.
    # Гостевая ОС и виртуальные CPU не запускаются; оба дескриптора закрываем.
    import fcntl
    try:
        device = os.open('/dev/kvm', os.O_RDWR | os.O_CLOEXEC)
        try:
            result['api_version'] = fcntl.ioctl(device, 0xAE00, 0)  # KVM_GET_API_VERSION
            vm = fcntl.ioctl(device, 0xAE01, 0)  # KVM_CREATE_VM
            os.close(vm)
            result['vm_created'] = True
            result['kvm_usable'] = result['api_version'] == 12
            result['reason'] = 'usable' if result['kvm_usable'] else 'unexpected_api_version'
        finally:
            os.close(device)
    except OSError as error:
        result['reason'] = 'kvm_operation_failed'
        result['error'] = str(error)
    return result


if __name__ == '__main__':
    # Выводим только техническую диагностику, не переменные окружения с секретами.
    report = json.dumps(probe(), indent=2)
    Path('runner-kvm.json').write_text(report + '\n', encoding='utf-8')
    print(report)
