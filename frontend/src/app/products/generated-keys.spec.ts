import { GeneratedKeys } from './generated-keys';

describe('GeneratedKeys', () => {
  let keys: GeneratedKeys;

  beforeEach(() => (keys = new GeneratedKeys()));

  it('hands the new services of a saved product to its page once', () => {
    keys.record(7, ['api', 'web']);

    expect(keys.take(7)).toEqual(['api', 'web']);
    expect(keys.take(7)).toEqual([]);
  });

  it('keeps the names of another product to itself and forgets them', () => {
    keys.record(7, ['api']);

    expect(keys.take(8)).toEqual([]);
    expect(keys.take(7)).toEqual([]);
  });

  it('forgets an earlier save when a save adds no service', () => {
    keys.record(7, ['api']);
    keys.record(7, []);

    expect(keys.take(7)).toEqual([]);
  });

  it('keeps its own copy of the names', () => {
    const names = ['api'];
    keys.record(7, names);
    names.push('web');

    expect(keys.take(7)).toEqual(['api']);
  });
});
