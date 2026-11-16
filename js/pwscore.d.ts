export declare const Level: Readonly<{
  EASY: 'EASY';
  MEDIUM: 'MEDIUM';
  STRONG: 'STRONG';
  VERY_STRONG: 'VERY_STRONG';
  EXTREMELY_STRONG: 'EXTREMELY_STRONG';
}>;

export type Level = (typeof Level)[keyof typeof Level];

/** Password strength score from 0 to 14. Throws TypeError for non-strings and RangeError for an empty string. */
export declare function score(password: string): number;

/** Password strength level. Throws like `score`. */
export declare function level(password: string): Level;
