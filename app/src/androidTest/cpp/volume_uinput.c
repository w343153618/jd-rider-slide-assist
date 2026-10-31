// Isolated Android-emulator test helper. Never built into the production APK.
#include <linux/input.h>
#include <linux/uinput.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void emit(int fd, unsigned short type, unsigned short code, int value) {
    struct input_event event = {0};
    event.type = type; event.code = code; event.value = value;
    if (write(fd, &event, sizeof(event)) != sizeof(event)) { perror("write input"); exit(2); }
    if (type != EV_SYN) emit(fd, EV_SYN, SYN_REPORT, 0);
}
int main(int argc, char **argv) {
    if (argc != 2) return 2;
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { perror("open uinput"); return 2; }
    if (ioctl(fd, UI_SET_EVBIT, EV_KEY) || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEUP)
            || ioctl(fd, UI_SET_KEYBIT, KEY_VOLUMEDOWN)) { perror("configure keys"); return 2; }
    struct uinput_setup config = {0};
    snprintf(config.name, sizeof(config.name), "SlideAssist isolated volume test");
    config.id.bustype = BUS_USB; config.id.vendor = 0x1234; config.id.product = 0x5678;
    if (ioctl(fd, UI_DEV_SETUP, &config) || ioctl(fd, UI_DEV_CREATE)) { perror("create device"); return 2; }
    usleep(700000); // Let InputReader register the test device.
    puts("READY"); fflush(stdout);
    if (!strcmp(argv[1], "single")) {
        emit(fd, EV_KEY, KEY_VOLUMEUP, 1); usleep(30000); emit(fd, EV_KEY, KEY_VOLUMEUP, 0);
    } else if (!strcmp(argv[1], "slow")) {
        emit(fd, EV_KEY, KEY_VOLUMEUP, 1); usleep(300000); emit(fd, EV_KEY, KEY_VOLUMEDOWN, 1);
        usleep(60000); emit(fd, EV_KEY, KEY_VOLUMEUP, 0); emit(fd, EV_KEY, KEY_VOLUMEDOWN, 0);
    } else if (!strcmp(argv[1], "hold") || !strcmp(argv[1], "reverse")) {
        int first = !strcmp(argv[1], "reverse") ? KEY_VOLUMEDOWN : KEY_VOLUMEUP;
        int second = first == KEY_VOLUMEUP ? KEY_VOLUMEDOWN : KEY_VOLUMEUP;
        emit(fd, EV_KEY, first, 1); usleep(30000); emit(fd, EV_KEY, second, 1);
        if (!strcmp(argv[1], "hold")) {
            usleep(150000); emit(fd, EV_KEY, first, 2); emit(fd, EV_KEY, second, 2);
            usleep(700000);
        } else usleep(80000);
        emit(fd, EV_KEY, first, 0); emit(fd, EV_KEY, second, 0);
    } else { ioctl(fd, UI_DEV_DESTROY); close(fd); return 2; }
    usleep(200000); puts("DONE"); fflush(stdout);
    ioctl(fd, UI_DEV_DESTROY); close(fd); return 0;
}
