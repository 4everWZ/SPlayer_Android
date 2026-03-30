const createLogger = (scope: string) => {
  const prefix = `[${scope}]`;
  return {
    info: (...args: unknown[]) => console.info(prefix, ...args),
    log: (...args: unknown[]) => console.log(prefix, ...args),
    warn: (...args: unknown[]) => console.warn(prefix, ...args),
    error: (...args: unknown[]) => console.error(prefix, ...args),
  };
};

export const serverLog = createLogger("server");
