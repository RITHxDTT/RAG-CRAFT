from collections import OrderedDict
from threading import Lock
from time import monotonic
from app.core.exceptions import AppError


class LoginLimiter:
    """Bounded, per-process V1 throttle; use a shared limiter for a multi-replica deployment."""
    def __init__(self, maximum=20, window=60, message="Too many attempts. Wait one minute before trying again."):
        self.maximum, self.window = maximum, window
        self.message = message
        self.attempts = OrderedDict()
        self.lock = Lock()

    def check(self, key):
        now = monotonic()
        with self.lock:
            count, start = self.attempts.get(key, (0, now))
            if now - start >= self.window:
                count, start = 0, now
            if count >= self.maximum:
                raise AppError(429, self.message)
            self.attempts[key] = (count + 1, start)
            self.attempts.move_to_end(key)
            while len(self.attempts) > 2048:
                self.attempts.popitem(last=False)


login_limiter = LoginLimiter()
