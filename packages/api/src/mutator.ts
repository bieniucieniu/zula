export const getApiBaseUrl = (): string =>
	(typeof import.meta !== "undefined" &&
		(import.meta as ImportMeta & { env?: Record<string, string> }).env
			?.VITE_API_BASE_URL) ||
	process.env.EXPO_PUBLIC_API_BASE_URL ||
	process.env.ZULA_API_BASE_URL ||
	"";

export const customInstance = async <T>(
	url: string,
	options: RequestInit,
): Promise<T> => {
	const res = await fetch(`${getApiBaseUrl()}${url}`, {
		...options,
		headers: {
			"Content-Type": "application/json",
			...options.headers,
		},
	});

	const text = await res.text();
	const data = text ? JSON.parse(text) : undefined;

	if (!res.ok) {
		throw data ?? new Error(res.statusText);
	}

	return data as T;
};
