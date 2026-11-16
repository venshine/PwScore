# frozen_string_literal: true

require_relative 'lib/pwscore'

Gem::Specification.new do |spec|
  spec.name = 'pwscore'
  spec.version = PwScore::VERSION
  spec.summary = 'Password strength scoring with no dependencies'
  spec.authors = ['venshine']
  spec.homepage = 'https://github.com/venshine/PwScore'
  spec.license = 'Apache-2.0'
  spec.required_ruby_version = '>= 2.6'
  spec.files = ['lib/pwscore.rb']
end
