
export abstract class CodeReviewError extends Error {
    constructor(message: string) {
        super(message);
    }
}

export class FileNotFoundError extends CodeReviewError {
    constructor(path: string) {
        super(`Path ${path} not found in filetree.`);
    }
}